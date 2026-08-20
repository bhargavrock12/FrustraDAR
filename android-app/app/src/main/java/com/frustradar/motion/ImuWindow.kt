package com.frustradar.motion

/**
 * Holds a single 2-second IMU window captured at 50 Hz (100 samples).
 *
 * Each sample has 6 axes: accelerometer (x, y, z) and gyroscope (x, y, z).
 * Sensor capture (SensorManager) is Phase 7; this is the data-transfer type
 * consumed by [MotionFeatureExtractor].
 *
 * @param accelX Accelerometer X-axis values (m/s²), size = [sampleCount].
 * @param accelY Accelerometer Y-axis values (m/s²), size = [sampleCount].
 * @param accelZ Accelerometer Z-axis values (m/s²), size = [sampleCount].
 * @param gyroX  Gyroscope X-axis values (rad/s),   size = [sampleCount].
 * @param gyroY  Gyroscope Y-axis values (rad/s),   size = [sampleCount].
 * @param gyroZ  Gyroscope Z-axis values (rad/s),   size = [sampleCount].
 * @param sampleCount Number of samples (expected 100 at 50 Hz × 2 s).
 */
data class ImuWindow(
    val accelX: FloatArray,
    val accelY: FloatArray,
    val accelZ: FloatArray,
    val gyroX: FloatArray,
    val gyroY: FloatArray,
    val gyroZ: FloatArray,
    val sampleCount: Int = 100
) {
    init {
        require(accelX.size == sampleCount) { "accelX size must be $sampleCount" }
        require(accelY.size == sampleCount) { "accelY size must be $sampleCount" }
        require(accelZ.size == sampleCount) { "accelZ size must be $sampleCount" }
        require(gyroX.size == sampleCount) { "gyroX size must be $sampleCount" }
        require(gyroY.size == sampleCount) { "gyroY size must be $sampleCount" }
        require(gyroZ.size == sampleCount) { "gyroZ size must be $sampleCount" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ImuWindow) return false
        return sampleCount == other.sampleCount &&
            accelX.contentEquals(other.accelX) &&
            accelY.contentEquals(other.accelY) &&
            accelZ.contentEquals(other.accelZ) &&
            gyroX.contentEquals(other.gyroX) &&
            gyroY.contentEquals(other.gyroY) &&
            gyroZ.contentEquals(other.gyroZ)
    }

    override fun hashCode(): Int {
        var result = accelX.contentHashCode()
        result = 31 * result + accelY.contentHashCode()
        result = 31 * result + accelZ.contentHashCode()
        result = 31 * result + gyroX.contentHashCode()
        result = 31 * result + gyroY.contentHashCode()
        result = 31 * result + gyroZ.contentHashCode()
        result = 31 * result + sampleCount
        return result
    }
}
