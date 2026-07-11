package com.frustradar.data.remote

import android.util.Log
import com.frustradar.auth.TokenManager
import com.frustradar.config.AppConfig
import com.frustradar.data.remote.dto.WsEventType
import com.frustradar.data.remote.dto.WsMessage
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp WebSocket client for real-time FrustraDAR events.
 *
 * Protocol (verified against `backend/app/websocket/ws_router.py`):
 * - Connect: `WS_BASE_URL?token=<jwt>`
 * - On connect success: receives `{"type":"connected","data":{"user_id","role","message"}}`
 * - Server sends `{"type":"ping"}` every 30s → client replies `{"type":"pong"}`
 * - Close code 4001 → Unauthorized (token invalid/expired) → triggers re-login
 * - Events: `frustration_score_updated`, `frustration_alert`, `session_started`,
 *           `session_ended`, `gaming_status_updated` — all with `{type, timestamp, data}` envelope
 */
@Singleton
class WebSocketClient @Inject constructor(
    private val tokenManager: TokenManager,
    private val appConfig: AppConfig
) {
    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var webSocket: WebSocket? = null
    private var isConnecting = false
    private var retryCount = 0

    private val _events = MutableSharedFlow<WsMessage>(extraBufferCapacity = 64)

    /** Observable stream of incoming WebSocket events. */
    val events: SharedFlow<WsMessage> = _events.asSharedFlow()

    private val _connectionState = MutableSharedFlow<ConnectionState>(
        replay = 1,
        extraBufferCapacity = 4
    )

    /** Observable connection state changes. */
    val connectionState: SharedFlow<ConnectionState> = _connectionState.asSharedFlow()

    /** Possible connection states. */
    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        UNAUTHORIZED
    }

    /**
     * Connect to the WebSocket server.
     * Uses the JWT from [TokenManager]. Does nothing if already connected or no token.
     */
    fun connect() {
        if (webSocket != null || isConnecting) return

        val token = tokenManager.getToken() ?: run {
            Log.w(TAG, "Cannot connect WS: no auth token")
            scope.launch { _connectionState.emit(ConnectionState.UNAUTHORIZED) }
            return
        }

        isConnecting = true
        scope.launch { _connectionState.emit(ConnectionState.CONNECTING) }

        val wsUrl = "${appConfig.wsBaseUrl}?token=$token"

        val client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS) // No read timeout for WebSocket
            .build()

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = client.newWebSocket(request, createListener())
    }

    /** Disconnect from the WebSocket server. Stops reconnection. */
    fun disconnect() {
        retryCount = MAX_RETRIES // Prevent reconnection
        webSocket?.close(NORMAL_CLOSURE, "Client disconnect")
        webSocket = null
        isConnecting = false
        scope.launch { _connectionState.emit(ConnectionState.DISCONNECTED) }
    }

    /** Send a raw JSON message. */
    fun send(message: String): Boolean {
        return webSocket?.send(message) ?: false
    }

    private fun createListener(): WebSocketListener = object : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.i(TAG, "WebSocket opened")
            isConnecting = false
            retryCount = 0
            // Don't emit CONNECTED yet — wait for the `connected` event from server
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                val message = gson.fromJson(text, WsMessage::class.java)

                when (message.type) {
                    WsEventType.CONNECTED -> {
                        Log.i(TAG, "WebSocket authenticated successfully")
                        scope.launch { _connectionState.emit(ConnectionState.CONNECTED) }
                        scope.launch { _events.emit(message) }
                    }

                    WsEventType.PING -> {
                        // Server heartbeat — reply with pong (per ws_router.py line 110-112)
                        val pong = gson.toJson(mapOf("type" to WsEventType.PONG))
                        webSocket.send(pong)
                    }

                    WsEventType.FRUSTRATION_SCORE_UPDATED,
                    WsEventType.FRUSTRATION_ALERT,
                    WsEventType.SESSION_STARTED,
                    WsEventType.SESSION_ENDED,
                    WsEventType.GAMING_STATUS_UPDATED -> {
                        scope.launch { _events.emit(message) }
                    }

                    else -> {
                        Log.d(TAG, "Unhandled WS event type: ${message.type}")
                        scope.launch { _events.emit(message) }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse WS message: ${text.take(100)}", e)
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.i(TAG, "WebSocket closing: code=$code reason=$reason")
            webSocket.close(NORMAL_CLOSURE, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.i(TAG, "WebSocket closed: code=$code reason=$reason")
            this@WebSocketClient.webSocket = null
            isConnecting = false

            when (code) {
                CLOSE_UNAUTHORIZED -> {
                    // Code 4001: token invalid — trigger re-login, do not reconnect
                    Log.w(TAG, "WebSocket 4001 Unauthorized — clearing token")
                    tokenManager.clearToken()
                    scope.launch { _connectionState.emit(ConnectionState.UNAUTHORIZED) }
                }
                else -> {
                    scope.launch { _connectionState.emit(ConnectionState.DISCONNECTED) }
                    scheduleReconnect()
                }
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "WebSocket failure: ${t.message}", t)
            this@WebSocketClient.webSocket = null
            isConnecting = false
            scope.launch { _connectionState.emit(ConnectionState.DISCONNECTED) }
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        if (retryCount >= MAX_RETRIES) {
            Log.w(TAG, "Max reconnection attempts reached ($MAX_RETRIES)")
            return
        }
        if (!tokenManager.isLoggedIn()) {
            Log.w(TAG, "Not reconnecting: no auth token")
            return
        }

        val delayMs = INITIAL_BACKOFF_MS * (1L shl retryCount.coerceAtMost(MAX_BACKOFF_SHIFT))
        retryCount++

        Log.i(TAG, "Scheduling WS reconnect in ${delayMs}ms (attempt $retryCount)")
        scope.launch {
            delay(delayMs)
            connect()
        }
    }

    companion object {
        private const val TAG = "FrustraDAR-WS"
        private const val NORMAL_CLOSURE = 1000
        private const val CLOSE_UNAUTHORIZED = 4001
        private const val MAX_RETRIES = 10
        private const val INITIAL_BACKOFF_MS = 1000L
        private const val MAX_BACKOFF_SHIFT = 5 // Max backoff = 32 seconds
    }
}
