package com.frustradar.scoring

/**
 * Converts XGBoost frustration probability to a 0–100 motion score.
 *
 * Formula (per `motion_scoring_config.json`):
 *   motion_score = frustration_probability × 100
 *
 * Temporal smoothing is **OFF** per checklist ML-D7 (null / not specified — do not invent).
 * Clipped to [0, 100].
 */
class MotionScorer {

    /**
     * @param probability Frustration probability from [com.frustradar.ml.MotionModel.infer],
     *   in range [0, 1].
     * @return Motion score in [0, 100].
     */
    fun score(probability: Float): Float {
        return (probability * 100f).coerceIn(0f, 100f)
    }
}
