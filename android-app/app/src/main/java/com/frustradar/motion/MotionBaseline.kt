package com.frustradar.motion

/**
 * Serializable representation of a per-user motion baseline.
 *
 * Stores the mean values of the 14 engineered features used as denominators
 * in baseline-ratio computation. Order matches `motion_features.json`.
 *
 * Persisted as encrypted JSON via [com.frustradar.data.local.BaselineStore].
 * Never uploaded (FD-1, FD-2).
 *
 * @param featureValues The 14 baseline feature means, in the exact order of
 *   `motion_feature_spec.json → personalized_features.names_ordered`:
 *   accel_std, accel_rms, accel_energy, accel_range,
 *   gyro_std, gyro_rms, gyro_energy, gyro_range,
 *   jerk_abs_mean, jerk_rms, jerk_energy,
 *   high_accel_count, high_gyro_count, accel_spike_count.
 * @param calibratedAtMillis Epoch millis when calibration completed.
 * @param windowCount Number of calibration windows used (expected 20).
 */
data class MotionBaseline(
    val featureValues: List<Float>,
    val calibratedAtMillis: Long,
    val windowCount: Int
) {
    init {
        require(featureValues.size == FEATURE_COUNT) {
            "Baseline must have exactly $FEATURE_COUNT features, got ${featureValues.size}"
        }
    }

    companion object {
        /** Number of baseline features = 14 (per motion_feature_spec.json). */
        const val FEATURE_COUNT = 14
    }
}
