package com.frustradar.scoring

/**
 * Complete result from a single scoring window, produced by [ScoringPipeline].
 *
 * @param facialScore  0–100 facial frustration score, or null if modality missing.
 * @param voiceScore   0–100 voice frustration score (= backend `audio_score`), or null.
 * @param motionScore  0–100 motion frustration score, or null.
 * @param fusionScore  0–100 fused final score. Null only if all modalities missing.
 * @param signalsUsed  List of modality names present (e.g., ["facial", "voice", "motion"]).
 * @param category     Display category band (calm/mild/moderate/high/critical), or null.
 * @param timestamp    ISO-8601 UTC timestamp of the scoring window.
 */
data class ScoringResult(
    val facialScore: Float?,
    val voiceScore: Float?,
    val motionScore: Float?,
    val fusionScore: Float?,
    val signalsUsed: List<String>,
    val category: String?,
    val timestamp: String
)
