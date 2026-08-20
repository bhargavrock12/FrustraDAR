package com.frustradar.motion

import com.frustradar.data.local.BaselineStore
import com.google.gson.Gson
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the per-user normal-motion baseline per `motion_baseline_spec.json`.
 *
 * Calibration collects [CALIBRATION_WINDOWS] (20) normal-motion windows, computes the
 * mean of each of the 14 relevant engineered features across those windows, and persists
 * the result via [BaselineStore] (encrypted SharedPreferences, on-device only, never uploaded).
 *
 * The 14 baseline features correspond to the denominators in baseline-ratio computation:
 *   accel_std, accel_rms, accel_energy, accel_range,
 *   gyro_std, gyro_rms, gyro_energy, gyro_range,
 *   jerk_abs_mean, jerk_rms, jerk_energy,
 *   high_accel_count, high_gyro_count, accel_spike_count
 *
 * Order matches `motion_feature_spec.json → personalized_features.names_ordered`.
 */
@Singleton
class MotionBaselineManager @Inject constructor(
    private val baselineStore: BaselineStore
) {
    private val gson = Gson()
    private val featureExtractor = MotionFeatureExtractor()

    /**
     * Index mapping: which of the 26 engineered features are used as baseline denominators.
     * Same indices as [MotionFeatureExtractor.baselineSourceIndices].
     */
    private val baselineFeatureIndices = intArrayOf(1, 6, 7, 4, 9, 14, 15, 12, 17, 21, 22, 23, 24, 25)

    /** Accumulator for calibration windows. Null when not calibrating. */
    private var calibrationBuffer: MutableList<FloatArray>? = null

    /** Cached baseline from store, lazily loaded. */
    private var cachedBaseline: MotionBaseline? = null

    /** Whether calibration has been completed and a baseline exists. */
    fun isCalibrated(): Boolean {
        if (cachedBaseline != null) return true
        return loadFromStore() != null
    }

    /**
     * Get the 14-feature baseline as a FloatArray, or null if not calibrated.
     * Used as the denominator array in [MotionFeatureExtractor.computeBaselineRatios].
     */
    fun getBaseline(): FloatArray? {
        val baseline = cachedBaseline ?: loadFromStore() ?: return null
        return baseline.featureValues.toFloatArray()
    }

    /** Start or restart calibration. Clears any in-progress calibration buffer. */
    fun startCalibration() {
        calibrationBuffer = mutableListOf()
    }

    /**
     * Add a calibration window. The 26 engineered features are extracted, and the
     * 14 relevant features are stored in the buffer.
     *
     * @param window A normal-motion IMU window.
     * @return true if calibration is complete (20 windows collected).
     */
    fun addCalibrationWindow(window: ImuWindow): Boolean {
        val buffer = calibrationBuffer
            ?: throw IllegalStateException("Call startCalibration() before adding windows")

        val engineered = featureExtractor.extractEngineered(window)
        val relevantFeatures = FloatArray(14) { i -> engineered[baselineFeatureIndices[i]] }
        buffer.add(relevantFeatures)

        if (buffer.size >= CALIBRATION_WINDOWS) {
            finalizeCalibration(buffer)
            calibrationBuffer = null
            return true
        }
        return false
    }

    /** Number of calibration windows collected so far, or 0 if not calibrating. */
    fun calibrationProgress(): Int = calibrationBuffer?.size ?: 0

    /** Clear stored baseline and restart. UI should prompt recalibration. */
    fun recalibrate() {
        cachedBaseline = null
        calibrationBuffer = null
        baselineStore.clear()
    }

    private fun finalizeCalibration(buffer: List<FloatArray>) {
        // Compute mean of each of the 14 features across all calibration windows.
        val means = FloatArray(14) { featureIndex ->
            buffer.map { it[featureIndex] }.average().toFloat()
        }

        val baseline = MotionBaseline(
            featureValues = means.toList(),
            calibratedAtMillis = System.currentTimeMillis(),
            windowCount = buffer.size
        )

        // Persist to encrypted store.
        val json = gson.toJson(baseline)
        baselineStore.saveBaseline(json)
        cachedBaseline = baseline
    }

    private fun loadFromStore(): MotionBaseline? {
        val json = baselineStore.getBaseline() ?: return null
        return try {
            val baseline = gson.fromJson(json, MotionBaseline::class.java)
            cachedBaseline = baseline
            baseline
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        /** Number of normal-motion windows required for calibration (per motion_baseline_spec.json). */
        const val CALIBRATION_WINDOWS = 20
    }
}
