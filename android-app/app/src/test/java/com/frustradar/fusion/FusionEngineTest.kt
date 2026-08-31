package com.frustradar.fusion

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for [FusionEngine].
 *
 * Covers:
 * - All-present: 0.375/0.3125/0.3125 renormalized weights
 * - Voice missing: 0.5455/0.4545
 * - Facial missing: 0.50/0.50
 * - Motion missing: 0.5455/0.4545
 * - Two missing: single modality gets weight 1.0
 * - All missing: returns null (no score)
 * - Clipping to 0–100
 * - Behavioral analytics NOT an input (no fourth parameter)
 */
class FusionEngineTest {

    private val engine = FusionEngine()

    @Test
    fun `all present - renormalized weights 0_375 0_3125 0_3125`() {
        // All modalities score 100 → fused = 100
        val result = engine.fuse(100f, 100f, 100f)!!
        assertEquals(100f, result.fusionScore, 0.01f)
        assertEquals(3, result.signalsUsed.size)

        // Verify renormalization: 50 facial, 50 voice, 50 motion → 50
        val result2 = engine.fuse(50f, 50f, 50f)!!
        assertEquals(50f, result2.fusionScore, 0.01f)
    }

    @Test
    fun `all present - asymmetric scores verify weights`() {
        // facial=100, voice=0, motion=0 → 0.375 × 100 + 0 + 0 = 37.5
        val result = engine.fuse(100f, 0f, 0f)!!
        assertEquals(37.5f, result.fusionScore, 0.01f)

        // facial=0, voice=100, motion=0 → 0 + 0.3125 × 100 + 0 = 31.25
        val result2 = engine.fuse(0f, 100f, 0f)!!
        assertEquals(31.25f, result2.fusionScore, 0.01f)

        // facial=0, voice=0, motion=100 → 0 + 0 + 0.3125 × 100 = 31.25
        val result3 = engine.fuse(0f, 0f, 100f)!!
        assertEquals(31.25f, result3.fusionScore, 0.01f)
    }

    @Test
    fun `voice missing - weights 0_5455 facial 0_4545 motion`() {
        // facial=100, motion=0 → 0.5455 × 100 = 54.55
        val result = engine.fuse(100f, null, 0f)!!
        assertEquals(54.545f, result.fusionScore, 0.1f)
        assertEquals(2, result.signalsUsed.size)
        assertTrue(result.signalsUsed.contains("facial"))
        assertTrue(result.signalsUsed.contains("motion"))
        assertFalse(result.signalsUsed.contains("voice"))
    }

    @Test
    fun `facial missing - weights 0_50 voice 0_50 motion`() {
        // voice=80, motion=40 → 0.50 × 80 + 0.50 × 40 = 60
        val result = engine.fuse(null, 80f, 40f)!!
        assertEquals(60f, result.fusionScore, 0.01f)
    }

    @Test
    fun `motion missing - weights 0_5455 facial 0_4545 voice`() {
        // facial=60, voice=80 → 0.5455 × 60 + 0.4545 × 80 = 32.73 + 36.36 = 69.09
        val result = engine.fuse(60f, 80f, null)!!
        assertEquals(69.09f, result.fusionScore, 0.2f)
    }

    @Test
    fun `two missing - facial only`() {
        val result = engine.fuse(75f, null, null)!!
        assertEquals(75f, result.fusionScore, 0.01f)
        assertEquals(listOf("facial"), result.signalsUsed)
    }

    @Test
    fun `two missing - voice only`() {
        val result = engine.fuse(null, 60f, null)!!
        assertEquals(60f, result.fusionScore, 0.01f)
        assertEquals(listOf("voice"), result.signalsUsed)
    }

    @Test
    fun `two missing - motion only`() {
        val result = engine.fuse(null, null, 90f)!!
        assertEquals(90f, result.fusionScore, 0.01f)
        assertEquals(listOf("motion"), result.signalsUsed)
    }

    @Test
    fun `all missing returns null`() {
        val result = engine.fuse(null, null, null)
        assertNull(result)
    }

    @Test
    fun `score clipped to 0-100`() {
        val result = engine.fuse(100f, 100f, 100f)!!
        assertTrue(result.fusionScore <= 100f)

        val result2 = engine.fuse(0f, 0f, 0f)!!
        assertTrue(result2.fusionScore >= 0f)
    }

    @Test
    fun `category bands - boundary values map upward`() {
        // 0 → calm
        assertEquals("calm", engine.fuse(0f, null, null)!!.category)
        // 19.9 → calm
        assertEquals("calm", engine.fuse(19.9f, null, null)!!.category)
        // 20 → mild (boundary maps upward)
        assertEquals("mild", engine.fuse(20f, null, null)!!.category)
        // 40 → moderate
        assertEquals("moderate", engine.fuse(40f, null, null)!!.category)
        // 60 → high
        assertEquals("high", engine.fuse(60f, null, null)!!.category)
        // 80 → critical
        assertEquals("critical", engine.fuse(80f, null, null)!!.category)
        // 100 → critical
        assertEquals("critical", engine.fuse(100f, null, null)!!.category)
    }

    @Test
    fun `signals_used reflects present modalities`() {
        val result = engine.fuse(50f, 50f, null)!!
        assertEquals(listOf("facial", "voice"), result.signalsUsed)
    }
}
