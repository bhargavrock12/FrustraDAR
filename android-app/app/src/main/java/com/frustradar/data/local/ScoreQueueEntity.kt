package com.frustradar.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for the offline score upload queue.
 *
 * Scores are buffered locally and uploaded in batches by the ScoreUploadWorker (Phase 3).
 * A score is marked `uploaded = true` only after a 201 response from `POST /scores/batch`.
 * Duplicate-on-retry is accepted/documented (checklist §3).
 *
 * Field naming follows the backend contract (`audio_score` ≡ Voice Score).
 */
@Entity(tableName = "score_queue")
data class ScoreQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Session UUID this score belongs to. */
    val sessionId: String,

    /** ISO-8601 UTC timestamp. */
    val timestamp: String,

    /** Individual signal scores (null if signal not available). Range 0–100. */
    val facialScore: Float? = null,
    val audioScore: Float? = null,   // ≡ Voice Score at contract layer
    val motionScore: Float? = null,
    val behaviorScore: Float? = null,

    /** Fused frustration score. Required, 0–100. */
    val fusionScore: Float,

    /** JSON-serialized list of signal names (e.g., ["facial","voice","motion"]). */
    val signalsUsed: String = "[]",

    /** Duration of the scoring window in seconds. */
    val windowDurationSec: Int,

    /** Whether this score has been successfully uploaded to the backend. */
    val uploaded: Boolean = false,

    /** When this entity was created locally (epoch millis). */
    val createdAt: Long = System.currentTimeMillis()
)
