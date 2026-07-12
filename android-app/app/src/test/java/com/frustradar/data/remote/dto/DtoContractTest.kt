package com.frustradar.data.remote.dto

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Test

/**
 * Contract tests verifying DTO serialization/deserialization matches
 * the exact JSON shapes produced by the FrustraDAR backend.
 *
 * JSON payloads are crafted from the backend `schemas/` source code
 * to validate field names, types, and nullability.
 */
class DtoContractTest {

    private val gson = Gson()

    // ── Auth DTOs ─────────────────────────────────────────────────────────

    @Test
    fun `TokenResponse deserializes from register 201 response`() {
        val json = """
        {
            "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test",
            "token_type": "bearer",
            "user": {
                "id": "550e8400-e29b-41d4-a716-446655440000",
                "email": "student@test.com",
                "username": "testuser",
                "role": "student",
                "parent_email": "parent@test.com",
                "is_active": true,
                "created_at": "2026-08-24T12:00:00"
            }
        }
        """.trimIndent()

        val response = gson.fromJson(json, TokenResponse::class.java)

        assertEquals("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test", response.accessToken)
        assertEquals("bearer", response.tokenType)
        assertNotNull(response.user)
        assertEquals("550e8400-e29b-41d4-a716-446655440000", response.user.id)
        assertEquals("student@test.com", response.user.email)
        assertEquals("testuser", response.user.username)
        assertEquals("student", response.user.role)
        assertEquals("parent@test.com", response.user.parentEmail)
        assertTrue(response.user.isActive)
        assertEquals("2026-08-24T12:00:00", response.user.createdAt)
    }

    @Test
    fun `TokenResponse deserializes with null parent_email`() {
        val json = """
        {
            "access_token": "token123",
            "token_type": "bearer",
            "user": {
                "id": "550e8400-e29b-41d4-a716-446655440000",
                "email": "parent@test.com",
                "username": "parentuser",
                "role": "parent",
                "parent_email": null,
                "is_active": true,
                "created_at": "2026-08-24T12:00:00"
            }
        }
        """.trimIndent()

        val response = gson.fromJson(json, TokenResponse::class.java)

        assertEquals("parent", response.user.role)
        assertNull(response.user.parentEmail)
    }

    @Test
    fun `UserCreateRequest serializes with correct field names`() {
        val request = UserCreateRequest(
            email = "test@test.com",
            username = "testuser",
            password = "password123",
            role = "student",
            parentEmail = "parent@test.com"
        )

        val json = gson.toJson(request)
        val obj = gson.fromJson(json, JsonObject::class.java)

        assertTrue(obj.has("email"))
        assertTrue(obj.has("username"))
        assertTrue(obj.has("password"))
        assertTrue(obj.has("role"))
        assertTrue(obj.has("parent_email"))
        assertEquals("parent@test.com", obj.get("parent_email").asString)
    }

    @Test
    fun `UserLoginRequest serializes with correct field names`() {
        val request = UserLoginRequest(email = "test@test.com", password = "pass123")
        val json = gson.toJson(request)
        val obj = gson.fromJson(json, JsonObject::class.java)

        assertTrue(obj.has("email"))
        assertTrue(obj.has("password"))
        assertFalse(obj.has("username")) // login doesn't have username
    }

    @Test
    fun `LinkParentRequest serializes with parent_email field`() {
        val request = LinkParentRequest(parentEmail = "parent@test.com")
        val json = gson.toJson(request)
        val obj = gson.fromJson(json, JsonObject::class.java)

        assertTrue(obj.has("parent_email"))
        assertEquals("parent@test.com", obj.get("parent_email").asString)
    }

    @Test
    fun `UpdateFcmTokenRequest serializes with fcm_token field`() {
        val request = UpdateFcmTokenRequest(fcmToken = "fcm-token-123")
        val json = gson.toJson(request)
        val obj = gson.fromJson(json, JsonObject::class.java)

        assertTrue(obj.has("fcm_token"))
        assertEquals("fcm-token-123", obj.get("fcm_token").asString)
    }

    @Test
    fun `ChildResponse deserializes correctly`() {
        val json = """
        {"id": "child-uuid", "username": "childuser", "email": "child@test.com"}
        """.trimIndent()

        val response = gson.fromJson(json, ChildResponse::class.java)

        assertEquals("child-uuid", response.id)
        assertEquals("childuser", response.username)
        assertEquals("child@test.com", response.email)
    }

    // ── Session DTOs ─────────────────────────────────────────────────────

    @Test
    fun `SessionStartRequest serializes with correct field names`() {
        val request = SessionStartRequest(
            gamePackage = "com.supercell.clashofclans",
            gameName = "Clash of Clans",
            startTime = "2026-08-24T12:00:00Z"
        )

        val json = gson.toJson(request)
        val obj = gson.fromJson(json, JsonObject::class.java)

        assertTrue(obj.has("game_package"))
        assertTrue(obj.has("game_name"))
        assertTrue(obj.has("start_time"))
        assertEquals("com.supercell.clashofclans", obj.get("game_package").asString)
    }

    @Test
    fun `SessionStartRequest serializes with null optional fields`() {
        val request = SessionStartRequest(startTime = "2026-08-24T12:00:00Z")

        val json = gson.toJson(request)
        val obj = gson.fromJson(json, JsonObject::class.java)

        assertTrue(obj.has("start_time"))
        // Null fields may or may not be present depending on Gson config, backend accepts both
    }

    @Test
    fun `SessionEndRequest serializes with correct field names`() {
        val request = SessionEndRequest(
            endTime = "2026-08-24T13:00:00Z",
            reopenCount = 3
        )

        val json = gson.toJson(request)
        val obj = gson.fromJson(json, JsonObject::class.java)

        assertTrue(obj.has("end_time"))
        assertTrue(obj.has("reopen_count"))
        assertEquals(3, obj.get("reopen_count").asInt)
    }

    @Test
    fun `SessionResponse deserializes all fields`() {
        val json = """
        {
            "id": "session-uuid",
            "game_package": "com.game.test",
            "game_name": "Test Game",
            "start_time": "2026-08-24T12:00:00",
            "end_time": "2026-08-24T13:00:00",
            "duration_sec": 3600,
            "is_night": false,
            "reopen_count": 2,
            "is_active": false
        }
        """.trimIndent()

        val response = gson.fromJson(json, SessionResponse::class.java)

        assertEquals("session-uuid", response.id)
        assertEquals("com.game.test", response.gamePackage)
        assertEquals("Test Game", response.gameName)
        assertEquals("2026-08-24T12:00:00", response.startTime)
        assertEquals("2026-08-24T13:00:00", response.endTime)
        assertEquals(3600, response.durationSec)
        assertFalse(response.isNight)
        assertEquals(2, response.reopenCount)
        assertFalse(response.isActive)
    }

    @Test
    fun `SessionResponse deserializes active session with null end_time`() {
        val json = """
        {
            "id": "session-uuid",
            "game_package": null,
            "game_name": null,
            "start_time": "2026-08-24T12:00:00",
            "end_time": null,
            "duration_sec": null,
            "is_night": true,
            "reopen_count": 0,
            "is_active": true
        }
        """.trimIndent()

        val response = gson.fromJson(json, SessionResponse::class.java)

        assertTrue(response.isActive)
        assertNull(response.endTime)
        assertNull(response.durationSec)
        assertNull(response.gamePackage)
        assertTrue(response.isNight)
    }

    // ── Score DTOs ─────────────────────────────────────────────────────

    @Test
    fun `ScoreBatchCreateRequest serializes with correct structure`() {
        val request = ScoreBatchCreateRequest(
            sessionId = "session-uuid",
            scores = listOf(
                ScoreCreateRequest(
                    timestamp = "2026-08-24T12:00:00Z",
                    facialScore = 45.0f,
                    audioScore = 30.0f,
                    motionScore = null,
                    fusionScore = 38.5f,
                    signalsUsed = listOf("facial", "voice"),
                    windowDurationSec = 30
                )
            )
        )

        val json = gson.toJson(request)
        val obj = gson.fromJson(json, JsonObject::class.java)

        assertTrue(obj.has("session_id"))
        assertEquals("session-uuid", obj.get("session_id").asString)
        assertTrue(obj.has("scores"))
        val scores = obj.getAsJsonArray("scores")
        assertEquals(1, scores.size())

        val score = scores[0].asJsonObject
        assertTrue(score.has("facial_score"))
        assertTrue(score.has("audio_score"))
        assertTrue(score.has("fusion_score"))
        assertTrue(score.has("signals_used"))
        assertTrue(score.has("window_duration_sec"))
        assertEquals(30, score.get("window_duration_sec").asInt)
    }

    @Test
    fun `ScoreBatchResponse deserializes from backend 201 response`() {
        val json = """
        {
            "message": "Scores uploaded successfully",
            "uploaded": 4,
            "max_score": 85.5
        }
        """.trimIndent()

        val response = gson.fromJson(json, ScoreBatchResponse::class.java)

        assertEquals("Scores uploaded successfully", response.message)
        assertEquals(4, response.uploaded)
        assertEquals(85.5f, response.maxScore, 0.01f)
    }

    @Test
    fun `ScoreResponse deserializes with nullable signal scores`() {
        val json = """
        {
            "id": "score-uuid",
            "timestamp": "2026-08-24T12:00:30",
            "facial_score": 45.0,
            "audio_score": null,
            "motion_score": 60.5,
            "behavior_score": null,
            "fusion_score": 52.75,
            "signals_used": ["facial", "motion"]
        }
        """.trimIndent()

        val response = gson.fromJson(json, ScoreResponse::class.java)

        assertEquals("score-uuid", response.id)
        assertEquals(45.0f, response.facialScore!!, 0.01f)
        assertNull(response.audioScore)
        assertEquals(60.5f, response.motionScore!!, 0.01f)
        assertNull(response.behaviorScore)
        assertEquals(52.75f, response.fusionScore, 0.01f)
        assertEquals(listOf("facial", "motion"), response.signalsUsed)
    }

    // ── Alert DTOs ─────────────────────────────────────────────────────

    @Test
    fun `AlertResponse deserializes all fields`() {
        val json = """
        {
            "id": "alert-uuid",
            "alert_type": "high_frustration",
            "severity": "high",
            "message": "Frustration level is high",
            "triggered_score": 75.5,
            "sent_at": "2026-08-24T12:01:00",
            "acknowledged": false
        }
        """.trimIndent()

        val response = gson.fromJson(json, AlertResponse::class.java)

        assertEquals("alert-uuid", response.id)
        assertEquals("high_frustration", response.alertType)
        assertEquals("high", response.severity)
        assertEquals("Frustration level is high", response.message)
        assertEquals(75.5f, response.triggeredScore!!, 0.01f)
        assertEquals("2026-08-24T12:01:00", response.sentAt)
        assertFalse(response.acknowledged)
    }

    @Test
    fun `AlertAcknowledgeResponse deserializes correctly`() {
        val json = """
        {"message": "Alert acknowledged", "alert_id": "alert-uuid"}
        """.trimIndent()

        val response = gson.fromJson(json, AlertAcknowledgeResponse::class.java)

        assertEquals("Alert acknowledged", response.message)
        assertEquals("alert-uuid", response.alertId)
    }

    @Test
    fun `UnreadCountResponse deserializes correctly`() {
        val json = """{"unread_count": 5}"""

        val response = gson.fromJson(json, UnreadCountResponse::class.java)

        assertEquals(5, response.unreadCount)
    }

    // ── User Profile DTOs ──────────────────────────────────────────────

    @Test
    fun `ProfileUpdateResponse deserializes correctly`() {
        val json = """{"message": "Profile updated", "username": "newname"}"""

        val response = gson.fromJson(json, ProfileUpdateResponse::class.java)

        assertEquals("Profile updated", response.message)
        assertEquals("newname", response.username)
    }

    @Test
    fun `AccountDeactivateResponse deserializes correctly`() {
        val json = """{"message": "Account deactivated"}"""

        val response = gson.fromJson(json, AccountDeactivateResponse::class.java)

        assertEquals("Account deactivated", response.message)
    }

    // ── WebSocket DTOs ──────────────────────────────────────────────────

    @Test
    fun `WsMessage deserializes connected event envelope`() {
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

        val msg = gson.fromJson(json, WsMessage::class.java)

        assertEquals("connected", msg.type)
        assertNotNull(msg.data)
        assertEquals("user-uuid", msg.data!!.get("user_id").asString)
        assertEquals("student", msg.data!!.get("role").asString)
    }

    @Test
    fun `WsMessage deserializes ping event`() {
        val json = """{"type": "ping"}"""

        val msg = gson.fromJson(json, WsMessage::class.java)

        assertEquals("ping", msg.type)
        assertNull(msg.data)
    }

    @Test
    fun `WsMessage deserializes frustration_score_updated event`() {
        val json = """
        {
            "type": "frustration_score_updated",
            "timestamp": "2026-08-24T12:00:30",
            "data": {
                "user_id": "user-uuid",
                "session_id": "session-uuid",
                "fusion_score": 72.5,
                "level": "high",
                "facial_score": 80.0,
                "audio_score": 65.0,
                "motion_score": null,
                "behavior_score": null,
                "signals_used": ["facial", "voice"]
            }
        }
        """.trimIndent()

        val msg = gson.fromJson(json, WsMessage::class.java)

        assertEquals("frustration_score_updated", msg.type)
        assertNotNull(msg.timestamp)
        assertNotNull(msg.data)
        assertEquals(72.5f, msg.data!!.get("fusion_score").asFloat, 0.01f)
        assertEquals("high", msg.data!!.get("level").asString)

        // Verify typed deserialization
        val data = gson.fromJson(msg.data, WsFrustrationScoreData::class.java)
        assertEquals("user-uuid", data.userId)
        assertEquals("session-uuid", data.sessionId)
        assertEquals(72.5f, data.fusionScore, 0.01f)
        assertEquals(listOf("facial", "voice"), data.signalsUsed)
    }

    @Test
    fun `WsMessage deserializes frustration_alert event`() {
        val json = """
        {
            "type": "frustration_alert",
            "timestamp": "2026-08-24T12:01:00",
            "data": {
                "alert_id": "alert-uuid",
                "alert_type": "critical_frustration",
                "severity": "critical",
                "message": "Critical frustration detected",
                "triggered_score": 92.0,
                "user_id": "user-uuid",
                "username": "testuser"
            }
        }
        """.trimIndent()

        val msg = gson.fromJson(json, WsMessage::class.java)

        assertEquals("frustration_alert", msg.type)

        val data = gson.fromJson(msg.data, WsFrustrationAlertData::class.java)
        assertEquals("alert-uuid", data.alertId)
        assertEquals("critical_frustration", data.alertType)
        assertEquals("critical", data.severity)
        assertEquals(92.0f, data.triggeredScore!!, 0.01f)
        assertEquals("testuser", data.username)
    }

    @Test
    fun `WsMessage deserializes session_started event`() {
        val json = """
        {
            "type": "session_started",
            "timestamp": "2026-08-24T23:30:00",
            "data": {
                "session_id": "session-uuid",
                "user_id": "user-uuid",
                "username": "testuser",
                "game_name": "Clash Royale",
                "game_package": "com.supercell.clashroyale",
                "start_time": "2026-08-24T23:30:00",
                "is_night": true
            }
        }
        """.trimIndent()

        val msg = gson.fromJson(json, WsMessage::class.java)

        val data = gson.fromJson(msg.data, WsSessionStartedData::class.java)
        assertEquals("session-uuid", data.sessionId)
        assertEquals("Clash Royale", data.gameName)
        assertTrue(data.isNight)
    }

    @Test
    fun `WsMessage deserializes session_ended event`() {
        val json = """
        {
            "type": "session_ended",
            "timestamp": "2026-08-24T13:00:00",
            "data": {
                "session_id": "session-uuid",
                "user_id": "user-uuid",
                "username": "testuser",
                "game_name": "Test Game",
                "duration_sec": 3600,
                "end_time": "2026-08-24T13:00:00"
            }
        }
        """.trimIndent()

        val msg = gson.fromJson(json, WsMessage::class.java)

        val data = gson.fromJson(msg.data, WsSessionEndedData::class.java)
        assertEquals("session-uuid", data.sessionId)
        assertEquals(3600, data.durationSec)
        assertEquals("Test Game", data.gameName)
    }

    @Test
    fun `WsEventType constants match backend EventType values`() {
        assertEquals("frustration_score_updated", WsEventType.FRUSTRATION_SCORE_UPDATED)
        assertEquals("frustration_alert", WsEventType.FRUSTRATION_ALERT)
        assertEquals("session_started", WsEventType.SESSION_STARTED)
        assertEquals("session_ended", WsEventType.SESSION_ENDED)
        assertEquals("gaming_status_updated", WsEventType.GAMING_STATUS_UPDATED)
        assertEquals("connected", WsEventType.CONNECTED)
        assertEquals("ping", WsEventType.PING)
        assertEquals("pong", WsEventType.PONG)
    }
}
