package com.frustradar.scoring

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for [AcousticAnalyzer].
 *
 * Covers:
 * - RMS energy calculation
 * - dB above baseline computation
 * - Acoustic score = clamp(dB/30 × 100, 0, 100)
 * - Spike detection at >15 dB threshold
 * - Rolling baseline update (10-window buffer)
 */
class AcousticAnalyzerTest {

    private lateinit var analyzer: AcousticAnalyzer

    @Before
    fun setUp() {
        analyzer = AcousticAnalyzer()
    }

    @Test
    fun `RMS of constant signal equals absolute value`() {
        val samples = FloatArray(100) { 0.5f }
        val result = analyzer.analyze(samples)
        assertEquals(0.5f, result.rmsEnergy, 0.001f)
    }

    @Test
    fun `first window has zero dB above baseline`() {
        val samples = FloatArray(100) { 0.1f }
        val result = analyzer.analyze(samples)

        // First window: baseline = current dB → dB_above = 0
        assertEquals(0f, result.dbAboveBaseline, 0.001f)
        assertEquals(0f, result.score, 0.001f)
    }

    @Test
    fun `louder window after quiet baseline has positive dB above`() {
        // Build up a quiet baseline
        val quietSamples = FloatArray(100) { 0.01f }
        repeat(5) { analyzer.analyze(quietSamples) }

        // Now a loud window
        val loudSamples = FloatArray(100) { 1.0f }
        val result = analyzer.analyze(loudSamples)

        assertTrue("dB above baseline should be positive", result.dbAboveBaseline > 0f)
        assertTrue("Score should be positive", result.score > 0f)
    }

    @Test
    fun `score formula clamp dB_div_30 times 100`() {
        // After establishing baseline, inject known dB difference
        val baseSamples = FloatArray(100) { 0.01f }
        repeat(10) { analyzer.analyze(baseSamples) }

        // Much louder signal → large dB above baseline
        val loudSamples = FloatArray(100) { 1.0f }
        val result = analyzer.analyze(loudSamples)

        // Expected dB above: 20 × log10(1.0/0.01) = 20 × 2 = 40 dB
        // Score = (40/30) × 100 = 133.3 → clamped to 100
        assertEquals(100f, result.score, 0.001f)
    }

    @Test
    fun `score clamped to 0 when quieter than baseline`() {
        // Build up loud baseline
        val loudSamples = FloatArray(100) { 1.0f }
        repeat(10) { analyzer.analyze(loudSamples) }

        // Quiet window
        val quietSamples = FloatArray(100) { 0.001f }
        val result = analyzer.analyze(quietSamples)

        // dB_above_baseline is clamped to ≥ 0, so score ≥ 0
        assertEquals(0f, result.dbAboveBaseline, 0.001f)
        assertEquals(0f, result.score, 0.001f)
    }

    @Test
    fun `spike detected when dB above baseline exceeds 15`() {
        val quietSamples = FloatArray(100) { 0.001f }
        repeat(10) { analyzer.analyze(quietSamples) }

        // Very loud → well above 15 dB
        val loudSamples = FloatArray(100) { 1.0f }
        val result = analyzer.analyze(loudSamples)

        assertTrue("Spike should be detected for >15 dB jump", result.spikeDetected)
    }

    @Test
    fun `no spike when dB change is small`() {
        val samples = FloatArray(100) { 0.5f }
        repeat(10) { analyzer.analyze(samples) }

        // Similar amplitude → small dB change
        val slightlyLouder = FloatArray(100) { 0.6f }
        val result = analyzer.analyze(slightlyLouder)

        assertFalse("No spike for small dB change", result.spikeDetected)
    }

    @Test
    fun `rolling baseline window is limited to 10`() {
        // Feed 20 quiet windows, then a loud one.
        val quietSamples = FloatArray(100) { 0.01f }
        repeat(20) { analyzer.analyze(quietSamples) }

        // The baseline should only reflect the last 10 windows.
        val loudSamples = FloatArray(100) { 1.0f }
        val result = analyzer.analyze(loudSamples)

        assertTrue("Score should be positive after rolling baseline", result.score > 0f)
    }

    @Test
    fun `reset clears baseline`() {
        val samples = FloatArray(100) { 0.5f }
        repeat(10) { analyzer.analyze(samples) }

        analyzer.reset()

        // After reset, first window should have 0 dB above baseline
        val result = analyzer.analyze(samples)
        assertEquals(0f, result.dbAboveBaseline, 0.001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects empty PCM`() {
        analyzer.analyze(FloatArray(0))
    }
}
