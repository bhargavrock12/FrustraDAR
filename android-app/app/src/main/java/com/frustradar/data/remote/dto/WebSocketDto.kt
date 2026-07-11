package com.frustradar.data.remote.dto

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

/**
 * WebSocket message DTOs.
 * The backend sends events as `{"type": "...", "timestamp": "...", "data": {...}}` envelopes
 * (verified against `websocket/events.py`).
 *
 * Special server messages:
 * - `{"type": "connected", "data": {"user_id", "role", "message"}}` — on connect
 * - `{"type": "ping"}` — heartbeat (server → client, every 30s)
 * - `{"type": "pong"}` — client → server reply to ping
 */

/** Generic WebSocket message envelope. */
data class WsMessage(
    val type: String,
    val data: JsonObject? = null,
    val timestamp: String? = null
)

// ── Typed event data ────────────────────────────────────────────────────

/** `connected` event data (sent once after WS auth succeeds) */
data class WsConnectedData(
    @SerializedName("user_id")
    val userId: String,
    val role: String,
    val message: String
)

/** `frustration_score_updated` event data */
data class WsFrustrationScoreData(
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("session_id")
    val sessionId: String?,
    @SerializedName("fusion_score")
    val fusionScore: Float,
    val level: String,
    @SerializedName("facial_score")
    val facialScore: Float?,
    @SerializedName("audio_score")
    val audioScore: Float?,
    @SerializedName("motion_score")
    val motionScore: Float?,
    @SerializedName("behavior_score")
    val behaviorScore: Float?,
    @SerializedName("signals_used")
    val signalsUsed: List<String>
)

/** `frustration_alert` event data */
data class WsFrustrationAlertData(
    @SerializedName("alert_id")
    val alertId: String,
    @SerializedName("alert_type")
    val alertType: String,
    val severity: String,
    val message: String,
    @SerializedName("triggered_score")
    val triggeredScore: Float?,
    @SerializedName("user_id")
    val userId: String,
    val username: String
)

/** `session_started` event data */
data class WsSessionStartedData(
    @SerializedName("session_id")
    val sessionId: String,
    @SerializedName("user_id")
    val userId: String,
    val username: String,
    @SerializedName("game_name")
    val gameName: String?,
    @SerializedName("game_package")
    val gamePackage: String?,
    @SerializedName("start_time")
    val startTime: String,
    @SerializedName("is_night")
    val isNight: Boolean
)

/** `session_ended` event data */
data class WsSessionEndedData(
    @SerializedName("session_id")
    val sessionId: String,
    @SerializedName("user_id")
    val userId: String,
    val username: String,
    @SerializedName("game_name")
    val gameName: String?,
    @SerializedName("duration_sec")
    val durationSec: Int?,
    @SerializedName("end_time")
    val endTime: String
)

/** `gaming_status_updated` event data */
data class WsGamingStatusData(
    @SerializedName("user_id")
    val userId: String,
    val username: String,
    @SerializedName("is_gaming")
    val isGaming: Boolean,
    @SerializedName("game_name")
    val gameName: String?,
    @SerializedName("current_score")
    val currentScore: Float?
)

/** Known WebSocket event types (matches `websocket/events.py EventType`) */
object WsEventType {
    const val CONNECTED = "connected"
    const val PING = "ping"
    const val PONG = "pong"
    const val FRUSTRATION_SCORE_UPDATED = "frustration_score_updated"
    const val FRUSTRATION_ALERT = "frustration_alert"
    const val SESSION_STARTED = "session_started"
    const val SESSION_ENDED = "session_ended"
    const val GAMING_STATUS_UPDATED = "gaming_status_updated"
}
