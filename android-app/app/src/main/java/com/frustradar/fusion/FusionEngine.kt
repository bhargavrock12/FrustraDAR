package com.frustradar.fusion

/**
 * Late-fusion scoring engine. Combines per-modality scores (0–100) into a
 * single Final Score (0–100) and a display category.
 *
 * Per `fusion_config.json` (FD-9…FD-13) and checklist §6:
 *   - Base weights: Facial 0.30, Voice 0.25, Motion 0.25
 *   - **Always renormalized** to sum to 1.0 (even when all present: 0.375/0.3125/0.3125)
 *   - All missing → no score (returns null)
 *   - Final score clipped 0–100
 *   - Behavioral analytics is NOT an input (FD-12)
 *
 * This is a scoring engine, NOT a trained model (FD-9, FD-24).
 */
class FusionEngine {

    private val categoryMapper = CategoryMapper()

    /**
     * Result of fusion.
     *
     * @param fusionScore  0–100 fused final score.
     * @param signalsUsed  List of present modality names.
     * @param category     Display category band.
     */
    data class FusionResult(
        val fusionScore: Float,
        val signalsUsed: List<String>,
        val category: String
    )

    /**
     * Fuse available modality scores into a final score.
     *
     * @param facialScore  0–100 facial score, or null if facial modality is missing.
     * @param voiceScore   0–100 voice score, or null if voice modality is missing.
     * @param motionScore  0–100 motion score, or null if motion modality is missing.
     * @return [FusionResult], or null if all modalities are missing.
     */
    fun fuse(facialScore: Float?, voiceScore: Float?, motionScore: Float?): FusionResult? {
        val present = mutableListOf<Pair<String, Float>>()
        var totalWeight = 0f

        if (facialScore != null) {
            present.add("facial" to facialScore)
            totalWeight += WEIGHT_FACIAL
        }
        if (voiceScore != null) {
            present.add("voice" to voiceScore)
            totalWeight += WEIGHT_VOICE
        }
        if (motionScore != null) {
            present.add("motion" to motionScore)
            totalWeight += WEIGHT_MOTION
        }

        // All missing → no score.
        if (present.isEmpty()) return null

        // Compute renormalized weighted sum.
        var weightedSum = 0f
        for ((name, score) in present) {
            val baseWeight = when (name) {
                "facial" -> WEIGHT_FACIAL
                "voice" -> WEIGHT_VOICE
                "motion" -> WEIGHT_MOTION
                else -> 0f
            }
            weightedSum += (baseWeight / totalWeight) * score
        }

        val clippedScore = weightedSum.coerceIn(0f, 100f)
        val category = categoryMapper.mapToCategory(clippedScore)
        val signalsUsed = present.map { it.first }

        return FusionResult(
            fusionScore = clippedScore,
            signalsUsed = signalsUsed,
            category = category
        )
    }

    companion object {
        /** Base weights per fusion_config.json. */
        private const val WEIGHT_FACIAL = 0.30f
        private const val WEIGHT_VOICE = 0.25f
        private const val WEIGHT_MOTION = 0.25f
    }
}
