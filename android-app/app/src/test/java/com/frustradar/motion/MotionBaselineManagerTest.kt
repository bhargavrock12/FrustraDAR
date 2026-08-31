package com.frustradar.motion

import com.frustradar.data.local.BaselineStore
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

/**
 * Tests for [MotionBaselineManager].
 *
 * Covers:
 * - Calibration requires exactly 20 windows
 * - Baseline stored/loaded via BaselineStore
 * - Recalibration clears and restarts
 * - isCalibrated() before/after calibration
 */
class MotionBaselineManagerTest {

    private lateinit var baselineStore: BaselineStore
    private lateinit var manager: MotionBaselineManager

    @Before
    fun setUp() {
        baselineStore = mock(BaselineStore::class.java)
        `when`(baselineStore.getBaseline()).thenReturn(null)
        `when`(baselineStore.isCalibrated()).thenReturn(false)
        manager = MotionBaselineManager(baselineStore)
    }

    private fun createConstantWindow(): ImuWindow {
        return ImuWindow(
            accelX = FloatArray(100) { 1.0f },
            accelY = FloatArray(100) { 1.0f },
            accelZ = FloatArray(100) { 1.0f },
            gyroX = FloatArray(100) { 0.5f },
            gyroY = FloatArray(100) { 0.5f },
            gyroZ = FloatArray(100) { 0.5f }
        )
    }

    @Test
    fun `not calibrated initially`() {
        assertFalse(manager.isCalibrated())
    }

    @Test
    fun `getBaseline returns null when not calibrated`() {
        assertNull(manager.getBaseline())
    }

    @Test
    fun `calibration requires exactly 20 windows`() {
        manager.startCalibration()
        val window = createConstantWindow()

        for (i in 1..19) {
            val complete = manager.addCalibrationWindow(window)
            assertFalse("Should not be complete after $i windows", complete)
            assertEquals(i, manager.calibrationProgress())
        }

        val complete = manager.addCalibrationWindow(window)
        assertTrue("Should be complete after 20 windows", complete)
    }

    @Test
    fun `calibration stores baseline via BaselineStore`() {
        manager.startCalibration()
        val window = createConstantWindow()

        repeat(20) { manager.addCalibrationWindow(window) }

        val captor = org.mockito.ArgumentCaptor.forClass(String::class.java)
        verify(baselineStore).saveBaseline(captor.capture() ?: "")
        val json = captor.value
        assertTrue(json.contains("featureValues") && json.contains("calibratedAtMillis"))
    }

    @Test
    fun `isCalibrated returns true after calibration`() {
        manager.startCalibration()
        val window = createConstantWindow()
        repeat(20) { manager.addCalibrationWindow(window) }

        assertTrue(manager.isCalibrated())
    }

    @Test
    fun `getBaseline returns 14 features after calibration`() {
        manager.startCalibration()
        val window = createConstantWindow()
        repeat(20) { manager.addCalibrationWindow(window) }

        val baseline = manager.getBaseline()
        assertNotNull(baseline)
        assertEquals(14, baseline!!.size)
    }

    @Test
    fun `recalibrate clears cached baseline`() {
        manager.startCalibration()
        val window = createConstantWindow()
        repeat(20) { manager.addCalibrationWindow(window) }
        assertTrue(manager.isCalibrated())

        manager.recalibrate()
        verify(baselineStore).clear()
        // After recalibrate + no stored data, should not be calibrated
        assertFalse(manager.isCalibrated())
    }

    @Test
    fun `loadFromStore restores baseline`() {
        val baseline = MotionBaseline(
            featureValues = List(14) { 1.5f },
            calibratedAtMillis = System.currentTimeMillis(),
            windowCount = 20
        )
        val json = Gson().toJson(baseline)
        `when`(baselineStore.getBaseline()).thenReturn(json)

        // Create new manager that will load from store.
        val manager2 = MotionBaselineManager(baselineStore)
        assertTrue(manager2.isCalibrated())

        val loaded = manager2.getBaseline()
        assertNotNull(loaded)
        assertEquals(14, loaded!!.size)
        assertEquals(1.5f, loaded[0], 0.001f)
    }

    @Test(expected = IllegalStateException::class)
    fun `addCalibrationWindow throws if startCalibration not called`() {
        manager.addCalibrationWindow(createConstantWindow())
    }

    @Test
    fun `calibration progress is 0 initially`() {
        assertEquals(0, manager.calibrationProgress())
    }
}
