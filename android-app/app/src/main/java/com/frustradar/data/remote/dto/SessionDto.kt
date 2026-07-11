package com.frustradar.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * DTOs for `/api/v1/sessions` endpoints.
 * Field names and types verified against backend `schemas/session.py`.
 */

// ── Request DTOs ──────────────────────────────────────────────────────────

/** POST /sessions/start — body */
data class SessionStartRequest(
    @SerializedName("game_package")
    val gamePackage: String? = null,
    @SerializedName("game_name")
    val gameName: String? = null,
    @SerializedName("start_time")
    val startTime: String // ISO-8601 UTC
)

/** PUT /sessions/{id}/end — body */
data class SessionEndRequest(
    @SerializedName("end_time")
    val endTime: String, // ISO-8601 UTC
    @SerializedName("reopen_count")
    val reopenCount: Int? = 0
)

// ── Response DTOs ──────────────────────────────────────────────────────────

/** Session response. Matches backend `SessionResponse` schema. */
data class SessionResponse(
    val id: String,
    @SerializedName("game_package")
    val gamePackage: String?,
    @SerializedName("game_name")
    val gameName: String?,
    @SerializedName("start_time")
    val startTime: String,
    @SerializedName("end_time")
    val endTime: String?,
    @SerializedName("duration_sec")
    val durationSec: Int?,
    @SerializedName("is_night")
    val isNight: Boolean,
    @SerializedName("reopen_count")
    val reopenCount: Int,
    @SerializedName("is_active")
    val isActive: Boolean
)
