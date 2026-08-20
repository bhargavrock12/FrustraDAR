package com.frustradar.motion

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Computes the 26 engineered features from a 2-second IMU window (100 samples at 50 Hz),
 * then produces the 14 baseline-ratio features that are the actual XGBoost model inputs.
 *
 * Feature names and order are sourced from:
 * - 26 engineered: `motion_feature_spec.json → engineered_features.names_ordered`
 * - 14 personalized: `motion_feature_spec.json → personalized_features.names_ordered`
 *   (confirmed identical to `motion_features.json`)
 *
 * **Feature order is the highest-risk contract.** A mismatch silently corrupts predictions.
 */
class MotionFeatureExtractor {

    /**
     * Index mapping from the 14 baseline-ratio features to their source in the 26 engineered features.
     *
     * Order from `motion_feature_spec.json → personalized_features.names_ordered`:
     *  0: accel_std_baseline_ratio     → engineered[1]  (accel_std)
     *  1: accel_rms_baseline_ratio     → engineered[6]  (accel_rms)
     *  2: accel_energy_baseline_ratio  → engineered[7]  (accel_energy)
     *  3: accel_range_baseline_ratio   → engineered[4]  (accel_range)
     *  4: gyro_std_baseline_ratio      → engineered[9]  (gyro_std)
     *  5: gyro_rms_baseline_ratio      → engineered[14] (gyro_rms)
     *  6: gyro_energy_baseline_ratio   → engineered[15] (gyro_energy)
     *  7: gyro_range_baseline_ratio    → engineered[12] (gyro_range)
     *  8: jerk_abs_mean_baseline_ratio → engineered[17] (jerk_abs_mean)
     *  9: jerk_rms_baseline_ratio      → engineered[21] (jerk_rms)
     * 10: jerk_energy_baseline_ratio   → engineered[22] (jerk_energy)
     * 11: high_accel_count_baseline_ratio  → engineered[23] (high_accel_count)
     * 12: high_gyro_count_baseline_ratio   → engineered[24] (high_gyro_count)
     * 13: accel_spike_count_baseline_ratio → engineered[25] (accel_spike_count)
     */
    private val baselineSourceIndices = intArrayOf(1, 6, 7, 4, 9, 14, 15, 12, 17, 21, 22, 23, 24, 25)

    /**
     * Extract the 26 engineered features from a raw IMU window.
     *
     * @param window 2-second IMU window (100 samples × 6 axes).
     * @return FloatArray of exactly 26 features in `motion_feature_spec.json` order.
     */
    fun extractEngineered(window: ImuWindow): FloatArray {
        val n = window.sampleCount

        // Compute acceleration magnitude per sample: sqrt(ax² + ay² + az²)
        val accelMag = FloatArray(n)
        for (i in 0 until n) {
            accelMag[i] = sqrt(
                window.accelX[i] * window.accelX[i] +
                window.accelY[i] * window.accelY[i] +
                window.accelZ[i] * window.accelZ[i]
            )
        }

        // Compute gyroscope magnitude per sample: sqrt(gx² + gy² + gz²)
        val gyroMag = FloatArray(n)
        for (i in 0 until n) {
            gyroMag[i] = sqrt(
                window.gyroX[i] * window.gyroX[i] +
                window.gyroY[i] * window.gyroY[i] +
                window.gyroZ[i] * window.gyroZ[i]
            )
        }

        // Compute jerk (derivative of acceleration magnitude) — (n-1) values
        val jerk = FloatArray(n - 1)
        for (i in 0 until n - 1) {
            jerk[i] = accelMag[i + 1] - accelMag[i]
        }

        // ── 26 engineered features in exact order from motion_feature_spec.json ──

        // Acceleration statistics (indices 0–7)
        val accelMean = accelMag.average().toFloat()                     // 0: accel_mean
        val accelStd = std(accelMag, accelMean)                          // 1: accel_std
        val accelMin = accelMag.min()                                    // 2: accel_min
        val accelMax = accelMag.max()                                    // 3: accel_max
        val accelRange = accelMax - accelMin                             // 4: accel_range
        val accelMedian = median(accelMag)                               // 5: accel_median
        val accelRms = rms(accelMag)                                     // 6: accel_rms
        val accelEnergy = energy(accelMag)                               // 7: accel_energy

        // Rotational statistics (indices 8–15)
        val gyroMean = gyroMag.average().toFloat()                       // 8: gyro_mean
        val gyroStd = std(gyroMag, gyroMean)                             // 9: gyro_std
        val gyroMin = gyroMag.min()                                      // 10: gyro_min
        val gyroMax = gyroMag.max()                                      // 11: gyro_max
        val gyroRange = gyroMax - gyroMin                                // 12: gyro_range
        val gyroMedian = median(gyroMag)                                 // 13: gyro_median
        val gyroRms = rms(gyroMag)                                       // 14: gyro_rms
        val gyroEnergy = energy(gyroMag)                                 // 15: gyro_energy

        // Jerk statistics (indices 16–22)
        val jerkMean = if (jerk.isNotEmpty()) jerk.average().toFloat() else 0f  // 16: jerk_mean
        val jerkAbsMean = if (jerk.isNotEmpty()) {                              // 17: jerk_abs_mean
            jerk.map { abs(it) }.average().toFloat()
        } else 0f
        val jerkStd = if (jerk.isNotEmpty()) std(jerk, jerkMean) else 0f       // 18: jerk_std
        val jerkMax = if (jerk.isNotEmpty()) jerk.max() else 0f                // 19: jerk_max
        val jerkRange = if (jerk.isNotEmpty()) jerk.max() - jerk.min() else 0f // 20: jerk_range
        val jerkRms = if (jerk.isNotEmpty()) rms(jerk) else 0f                 // 21: jerk_rms
        val jerkEnergy = if (jerk.isNotEmpty()) energy(jerk) else 0f           // 22: jerk_energy

        // Movement-event counts (indices 23–25)
        val highAccelThreshold = accelMean + 2 * accelStd
        val highGyroThreshold = gyroMean + 2 * gyroStd
        val highAccelCount = accelMag.count { it > highAccelThreshold }.toFloat()   // 23: high_accel_count
        val highGyroCount = gyroMag.count { it > highGyroThreshold }.toFloat()      // 24: high_gyro_count

        // accel_spike_count: consecutive pairs where jerk exceeds mean+2*std
        val spikeThreshold = if (jerk.isNotEmpty()) {
            jerkAbsMean + 2 * (if (jerk.isNotEmpty()) std(jerk.map { abs(it) }.toFloatArray(), jerkAbsMean) else 0f)
        } else 0f
        val accelSpikeCount = if (jerk.isNotEmpty()) {
            jerk.count { abs(it) > spikeThreshold }.toFloat()
        } else 0f                                                                     // 25: accel_spike_count

        return floatArrayOf(
            accelMean, accelStd, accelMin, accelMax, accelRange, accelMedian, accelRms, accelEnergy,
            gyroMean, gyroStd, gyroMin, gyroMax, gyroRange, gyroMedian, gyroRms, gyroEnergy,
            jerkMean, jerkAbsMean, jerkStd, jerkMax, jerkRange, jerkRms, jerkEnergy,
            highAccelCount, highGyroCount, accelSpikeCount
        )
    }

    /**
     * Compute the 14 baseline-ratio features from current-window engineered features
     * and baseline values.
     *
     * Ratio = current / baseline for each of the 14 features.
     * Division-by-zero protection: if baseline ≈ 0, ratio = 1.0 (documented implementation choice).
     *
     * @param engineered 26 engineered features from [extractEngineered].
     * @param baseline   14 baseline feature means from [MotionBaselineManager.getBaseline].
     * @return FloatArray of exactly 14 features in `motion_features.json` order.
     */
    fun computeBaselineRatios(engineered: FloatArray, baseline: FloatArray): FloatArray {
        require(engineered.size == 26) { "Expected 26 engineered features, got ${engineered.size}" }
        require(baseline.size == 14) { "Expected 14 baseline features, got ${baseline.size}" }

        return FloatArray(14) { i ->
            val currentValue = engineered[baselineSourceIndices[i]]
            val baselineValue = baseline[i]
            if (abs(baselineValue) < ZERO_THRESHOLD) {
                1.0f // documented implementation choice: no deviation signal
            } else {
                currentValue / baselineValue
            }
        }
    }

    // ── Statistical helpers ──────────────────────────────────────────────

    private fun std(values: FloatArray, mean: Float): Float {
        if (values.isEmpty()) return 0f
        var sumSq = 0.0
        for (v in values) {
            val diff = v - mean
            sumSq += diff * diff
        }
        return sqrt(sumSq / values.size).toFloat()
    }

    private fun rms(values: FloatArray): Float {
        if (values.isEmpty()) return 0f
        var sumSq = 0.0
        for (v in values) {
            sumSq += v * v
        }
        return sqrt(sumSq / values.size).toFloat()
    }

    private fun energy(values: FloatArray): Float {
        var sum = 0.0
        for (v in values) {
            sum += v * v
        }
        return sum.toFloat()
    }

    private fun median(values: FloatArray): Float {
        val sorted = values.copyOf().also { it.sort() }
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[mid - 1] + sorted[mid]) / 2f
        } else {
            sorted[mid]
        }
    }

    companion object {
        /** Threshold below which a baseline value is treated as zero. */
        private const val ZERO_THRESHOLD = 1e-7f
    }
}
