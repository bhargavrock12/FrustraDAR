package com.frustradar.motion

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for [MotionFeatureExtractor].
 *
 * Covers:
 * - 26 engineered features from a known IMU window
 * - Feature order matches motion_feature_spec.json exactly
 * - 14 baseline-ratio features in exact order from motion_features.json
 * - Feature vector length = 14
 * - Division-by-zero protection when baseline is 0
 */
class MotionFeatureExtractorTest {

    private val extractor = MotionFeatureExtractor()

    /** Create a constant-value IMU window for predictable feature computation. */
    private fun constantWindow(
        accelVal: Float = 1.0f,
        gyroVal: Float = 0.5f,
        n: Int = 100
    ): ImuWindow {
        return ImuWindow(
            accelX = FloatArray(n) { accelVal },
            accelY = FloatArray(n) { accelVal },
            accelZ = FloatArray(n) { accelVal },
            gyroX = FloatArray(n) { gyroVal },
            gyroY = FloatArray(n) { gyroVal },
            gyroZ = FloatArray(n) { gyroVal },
            sampleCount = n
        )
    }

    @Test
    fun `extractEngineered returns exactly 26 features`() {
        val window = constantWindow()
        val features = extractor.extractEngineered(window)
        assertEquals(26, features.size)
    }

    @Test
    fun `constant acceleration produces zero std and range`() {
        val window = constantWindow(accelVal = 2.0f)
        val features = extractor.extractEngineered(window)

        // accel_mag = sqrt(2² + 2² + 2²) = sqrt(12) ≈ 3.4641
        val expectedMag = kotlin.math.sqrt(12f)

        // Index 0: accel_mean
        assertEquals(expectedMag, features[0], 0.001f)
        // Index 1: accel_std — constant → 0
        assertEquals(0f, features[1], 0.001f)
        // Index 4: accel_range — constant → 0
        assertEquals(0f, features[4], 0.001f)
    }

    @Test
    fun `constant gyro produces zero std and range`() {
        val window = constantWindow(gyroVal = 1.0f)
        val features = extractor.extractEngineered(window)

        // gyro_mag = sqrt(1² + 1² + 1²) = sqrt(3)
        val expectedMag = kotlin.math.sqrt(3f)

        // Index 8: gyro_mean
        assertEquals(expectedMag, features[8], 0.001f)
        // Index 9: gyro_std — constant → 0
        assertEquals(0f, features[9], 0.001f)
        // Index 12: gyro_range — constant → 0
        assertEquals(0f, features[12], 0.001f)
    }

    @Test
    fun `constant acceleration produces zero jerk features`() {
        val window = constantWindow()
        val features = extractor.extractEngineered(window)

        // Indices 16–22: jerk features — all zero since accel_mag is constant.
        for (i in 16..22) {
            assertEquals("Feature at index $i should be 0", 0f, features[i], 0.001f)
        }
    }

    @Test
    fun `accel_rms equals accel_mean for constant signal`() {
        val window = constantWindow(accelVal = 3.0f)
        val features = extractor.extractEngineered(window)

        // For constant signal, mean == rms
        assertEquals(features[0], features[6], 0.001f)
    }

    @Test
    fun `accel_energy equals n times magnitude_squared for constant signal`() {
        val window = constantWindow(accelVal = 1.0f)
        val features = extractor.extractEngineered(window)

        // accel_mag = sqrt(3), energy = 100 * 3 = 300
        assertEquals(300f, features[7], 0.1f)
    }

    @Test
    fun `feature order matches motion_feature_spec json`() {
        // Verify the 26 feature names in order against the canonical spec.
        // We test this indirectly by verifying specific known positions.
        val window = ImuWindow(
            accelX = FloatArray(100) { if (it < 50) 1f else 3f },
            accelY = FloatArray(100) { 0f },
            accelZ = FloatArray(100) { 0f },
            gyroX = FloatArray(100) { 0f },
            gyroY = FloatArray(100) { 0f },
            gyroZ = FloatArray(100) { 0f }
        )
        val features = extractor.extractEngineered(window)

        // accel_mean (0) should be average of magnitudes (mix of 1 and 3)
        val expectedMean = (50 * 1f + 50 * 3f) / 100f
        assertEquals(expectedMean, features[0], 0.001f)

        // accel_min (2) = 1.0
        assertEquals(1.0f, features[2], 0.001f)
        // accel_max (3) = 3.0
        assertEquals(3.0f, features[3], 0.001f)
        // accel_range (4) = 2.0
        assertEquals(2.0f, features[4], 0.001f)
    }

    @Test
    fun `computeBaselineRatios returns exactly 14 features`() {
        val window = constantWindow()
        val engineered = extractor.extractEngineered(window)
        val baseline = FloatArray(14) { 1.0f }

        val ratios = extractor.computeBaselineRatios(engineered, baseline)
        assertEquals(14, ratios.size)
    }

    @Test
    fun `computeBaselineRatios produces correct ratios`() {
        val window = constantWindow(accelVal = 2.0f)
        val engineered = extractor.extractEngineered(window)

        // Baseline where each value is half of the current → ratio should be 2.0
        val baseline = FloatArray(14) { i ->
            // Source indices: [1, 6, 7, 4, 9, 14, 15, 12, 17, 21, 22, 23, 24, 25]
            val sourceIndices = intArrayOf(1, 6, 7, 4, 9, 14, 15, 12, 17, 21, 22, 23, 24, 25)
            engineered[sourceIndices[i]] / 2f
        }

        val ratios = extractor.computeBaselineRatios(engineered, baseline)

        for (i in ratios.indices) {
            if (baseline[i] > 1e-7f) {
                assertEquals("Ratio at index $i", 2.0f, ratios[i], 0.001f)
            }
        }
    }

    @Test
    fun `computeBaselineRatios zero baseline returns 1`() {
        val engineered = FloatArray(26) { 5.0f }
        val baseline = FloatArray(14) { 0.0f }

        val ratios = extractor.computeBaselineRatios(engineered, baseline)

        for (i in ratios.indices) {
            assertEquals("Zero baseline at index $i should give ratio 1.0", 1.0f, ratios[i], 0.001f)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `computeBaselineRatios rejects wrong engineered size`() {
        extractor.computeBaselineRatios(FloatArray(10), FloatArray(14))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `computeBaselineRatios rejects wrong baseline size`() {
        extractor.computeBaselineRatios(FloatArray(26), FloatArray(10))
    }

    @Test
    fun `ratio feature order matches motion_features json`() {
        // The 14 ratio features must use these source indices from the 26 engineered features:
        // [1, 6, 7, 4, 9, 14, 15, 12, 17, 21, 22, 23, 24, 25]
        // Corresponding to: accel_std, accel_rms, accel_energy, accel_range,
        //   gyro_std, gyro_rms, gyro_energy, gyro_range,
        //   jerk_abs_mean, jerk_rms, jerk_energy,
        //   high_accel_count, high_gyro_count, accel_spike_count

        // Create an engineered array where each index == its value for tracing.
        val engineered = FloatArray(26) { it.toFloat() }
        val baseline = FloatArray(14) { 1.0f }

        val ratios = extractor.computeBaselineRatios(engineered, baseline)

        val expectedSourceIndices = intArrayOf(1, 6, 7, 4, 9, 14, 15, 12, 17, 21, 22, 23, 24, 25)
        for (i in ratios.indices) {
            assertEquals(
                "Ratio[$i] should source from engineered[${expectedSourceIndices[i]}]",
                expectedSourceIndices[i].toFloat(),
                ratios[i],
                0.001f
            )
        }
    }
}
