package com.frustradar.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * DTOs for `/api/v1/alerts` endpoints.
 * Field names and types verified against backend `schemas/alert.py` and `routes/alerts.py`.
 */

// ── Response DTOs ──────────────────────────────────────────────────────────

/** GET /alerts/ — each alert */
data class AlertResponse(
    val id: String,
    @SerializedName("alert_type")
    val alertType: String,
    val severity: String,
    val message: String?,
    @SerializedName("triggered_score")
    val triggeredScore: Float?,
    @SerializedName("sent_at")
    val sentAt: String,
    val acknowledged: Boolean
)

/** PUT /alerts/{id}/acknowledge — response (no request body needed) */
data class AlertAcknowledgeResponse(
    val message: String,
    @SerializedName("alert_id")
    val alertId: String
)

/** GET /alerts/unread-count — response */
data class UnreadCountResponse(
    @SerializedName("unread_count")
    val unreadCount: Int
)
