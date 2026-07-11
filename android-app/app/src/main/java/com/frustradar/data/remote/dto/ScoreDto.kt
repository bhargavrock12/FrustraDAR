package com.frustradar.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * DTOs for `/api/v1/scores` endpoints.
 * Field names and types verified against backend `schemas/score.py`.
 *
 * Note on `audio_score`: the backend/DB field is `audio_score`. The ML/product term is "Voice".
 * At the contract layer `audio_score` ≡ Voice Score (04_API_CONTRACT.md).
 */

// ── Request DTOs ──────────────────────────────────────────────────────────

/** Individual score entry within a batch upload. */
data class ScoreCreateRequest(
    val timestamp: String, // ISO-8601 UTC, required
    @SerializedName("facial_score")
    val facialScore: Float? = null,
    @SerializedName("audio_score")
    val audioScore: Float? = null,
    @SerializedName("motion_score")
    val motionScore: Float? = null,
    @SerializedName("behavior_score")
    val behaviorScore: Float? = null,
    @SerializedName("fusion_score")
    val fusionScore: Float, // required, 0–100
    @SerializedName("signals_used")
    val signalsUsed: List<String> = emptyList(),
    @SerializedName("window_duration_sec")
    val windowDurationSec: Int // no default — caller specifies (checklist says 30, backend default 90)
)

/** POST /scores/batch — body */
data class ScoreBatchCreateRequest(
    @SerializedName("session_id")
    val sessionId: String,
    val scores: List<ScoreCreateRequest>
)

// ── Response DTOs ──────────────────────────────────────────────────────────

/** POST /scores/batch → 201 response */
data class ScoreBatchResponse(
    val message: String,
    val uploaded: Int,
    @SerializedName("max_score")
    val maxScore: Float
)

/** GET /scores/latest, /scores/session/{id} — each score */
data class ScoreResponse(
    val id: String,
    val timestamp: String,
    @SerializedName("facial_score")
    val facialScore: Float?,
    @SerializedName("audio_score")
    val audioScore: Float?,
    @SerializedName("motion_score")
    val motionScore: Float?,
    @SerializedName("behavior_score")
    val behaviorScore: Float?,
    @SerializedName("fusion_score")
    val fusionScore: Float,
    @SerializedName("signals_used")
    val signalsUsed: List<String>
)
