package com.frustradar.fusion

/**
 * Maps a fusion score (0–100) to a display category band.
 *
 * Per `fusion_config.json`:
 *   - [0, 20)   → calm
 *   - [20, 40)  → mild
 *   - [40, 60)  → moderate
 *   - [60, 80)  → high
 *   - [80, 100] → critical
 *
 * Bands are lower-inclusive, upper-exclusive: exact boundary values map
 * to the higher band (e.g. 20 → mild, 80 → critical).
 * The top band includes 100: [80, 100].
 *
 * Category bands are **display-only** and are distinct from backend alert
 * thresholds (HIGH 70 / CRITICAL 85, FD-15). Do not conflate them.
 */
class CategoryMapper {

    /**
     * @param score Fusion score in [0, 100].
     * @return Category label: one of calm, mild, moderate, high, critical.
     */
    fun mapToCategory(score: Float): String {
        return when {
            score < 20f -> CALM
            score < 40f -> MILD
            score < 60f -> MODERATE
            score < 80f -> HIGH
            else -> CRITICAL
        }
    }

    companion object {
        const val CALM = "calm"
        const val MILD = "mild"
        const val MODERATE = "moderate"
        const val HIGH = "high"
        const val CRITICAL = "critical"
    }
}
