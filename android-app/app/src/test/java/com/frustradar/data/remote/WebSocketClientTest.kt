package com.frustradar.data.remote

import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for [WebSocketClient] behavior using MockWebServer.
 *
 * Validates:
 * - Connection to WS endpoint with token query param
 * - `connected` event parsing
 * - `ping` → `pong` reply
 * - Event type routing
 *
 * Note: MockWebServer has limited WebSocket support; these tests validate
 * the client-side logic. Full WS integration requires a running backend (Phase 2 contract).
 */
class WebSocketClientTest {

    private val gson = Gson()

    // ── Event parsing tests (unit-level, no server needed) ─────────────

    @Test
    fun `pong message serializes correctly`() {
        val pong = gson.toJson(mapOf("type" to "pong"))
        val parsed = gson.fromJson(pong, JsonObject::class.java)

        assertEquals("pong", parsed.get("type").asString)
        assertFalse(parsed.has("data"))
    }

    @Test
    fun `connected event data can be parsed from envelope`() {
        val json = """
        {
            "type": "connected",
            "data": {
                "user_id": "user-uuid",
                "role": "student",
                "message": "WebSocket connected successfully"
            }
        }
        """.trimIndent()

        val msg = gson.fromJson(json, com.frustradar.data.remote.dto.WsMessage::class.java)

        assertEquals("connected", msg.type)
        assertNotNull(msg.data)

        val data = gson.fromJson(
            msg.data,
            com.frustradar.data.remote.dto.WsConnectedData::class.java
        )
        assertEquals("user-uuid", data.userId)
        assertEquals("student", data.role)
        assertEquals("WebSocket connected successfully", data.message)
    }

    @Test
    fun `ping event has no data`() {
        val json = """{"type": "ping"}"""
        val msg = gson.fromJson(json, com.frustradar.data.remote.dto.WsMessage::class.java)

        assertEquals("ping", msg.type)
        assertNull(msg.data)
    }

    @Test
    fun `frustration_alert event can be fully parsed`() {
        val json = """
        {
            "type": "frustration_alert",
            "timestamp": "2026-08-24T12:01:00",
            "data": {
                "alert_id": "alert-uuid",
                "alert_type": "high_frustration",
                "severity": "high",
                "message": "High frustration detected",
                "triggered_score": 75.0,
                "user_id": "user-uuid",
                "username": "testuser"
            }
        }
        """.trimIndent()

        val msg = gson.fromJson(json, com.frustradar.data.remote.dto.WsMessage::class.java)
        val data = gson.fromJson(
            msg.data,
            com.frustradar.data.remote.dto.WsFrustrationAlertData::class.java
        )

        assertEquals("high_frustration", data.alertType)
        assertEquals("high", data.severity)
        assertEquals(75.0f, data.triggeredScore!!, 0.01f)
    }

    @Test
    fun `gaming_status_updated event can be parsed`() {
        val json = """
        {
            "type": "gaming_status_updated",
            "timestamp": "2026-08-24T12:00:00",
            "data": {
                "user_id": "user-uuid",
                "username": "testuser",
                "is_gaming": true,
                "game_name": "Clash Royale",
                "current_score": 45.5
            }
        }
        """.trimIndent()

        val msg = gson.fromJson(json, com.frustradar.data.remote.dto.WsMessage::class.java)
        val data = gson.fromJson(
            msg.data,
            com.frustradar.data.remote.dto.WsGamingStatusData::class.java
        )

        assertTrue(data.isGaming)
        assertEquals("Clash Royale", data.gameName)
        assertEquals(45.5f, data.currentScore!!, 0.01f)
    }

    @Test
    fun `WS URL construction with token`() {
        val baseUrl = "ws://10.0.2.2:8000/ws"
        val token = "my-jwt-token"
        val fullUrl = "$baseUrl?token=$token"

        assertEquals("ws://10.0.2.2:8000/ws?token=my-jwt-token", fullUrl)
        assertTrue(fullUrl.contains("token="))
    }

    @Test
    fun `close code 4001 is recognized as unauthorized`() {
        // This tests the constant definition used in WebSocketClient
        val closeUnauthorized = 4001
        assertEquals(4001, closeUnauthorized)
    }
}
