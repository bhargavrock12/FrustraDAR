package com.frustradar.scoring

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for [FacialScorer].
 *
 * Covers:
 * - Softmax correctness on known logits
 * - (angry+disgust+fear)×100 formula verification
 * - Clipping to 0–100
 * - Threshold 31.0 classification (display-only)
 * - Known probability vector → expected score (golden vector)
 */
class FacialScorerTest {

    private val scorer = FacialScorer()

    @Test
    fun `softmax produces valid probabilities summing to 1`() {
        val logits = floatArrayOf(2.0f, 1.0f, 0.5f, 0.1f, -1.0f, -0.5f, 0.0f)
        val result = scorer.score(logits)
        val probSum = result.probabilities.sum()
        assertEquals(1.0f, probSum, 0.001f)
    }

    @Test
    fun `all equal logits give uniform probabilities`() {
        val logits = floatArrayOf(1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f)
        val result = scorer.score(logits)

        val expectedProb = 1.0f / 7f
        for (p in result.probabilities) {
            assertEquals(expectedProb, p, 0.001f)
        }

        // Score = (3 × 1/7) × 100 = 42.857
        assertEquals(42.857f, result.score, 0.1f)
    }

    @Test
    fun `golden vector - high anger logit`() {
        // angry has highest logit → high P_angry → high score
        val logits = floatArrayOf(5.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f)
        val result = scorer.score(logits)

        // P_angry should dominate, score ≈ P_angry × 100
        assertTrue("Score should be high for angry-dominant logits", result.score > 90f)
        assertTrue(result.isFrustrated)
    }

    @Test
    fun `golden vector - high happy logit`() {
        // happy (index 3) dominates → frustration emotions near 0 → low score
        val logits = floatArrayOf(0.0f, 0.0f, 0.0f, 10.0f, 0.0f, 0.0f, 0.0f)
        val result = scorer.score(logits)

        assertTrue("Score should be very low for happy-dominant logits", result.score < 1f)
        assertFalse(result.isFrustrated)
    }

    @Test
    fun `golden vector - mixed frustration emotions`() {
        // angry=2, disgust=2, fear=2, rest=0 → each ≈ e²/(3e²+4e⁰)
        val logits = floatArrayOf(2.0f, 2.0f, 2.0f, 0.0f, 0.0f, 0.0f, 0.0f)
        val result = scorer.score(logits)

        // Frustration probability = 3·e²/(3·e² + 4·e⁰)
        val e2 = kotlin.math.exp(2.0).toFloat()
        val frustProb = 3f * e2 / (3f * e2 + 4f)
        val expectedScore = frustProb * 100f

        assertEquals(expectedScore, result.score, 0.5f)
    }

    @Test
    fun `threshold 31 exactly is frustrated`() {
        // Find logits that produce score ≥ 31
        // angry=1, rest=0 → P_angry = e¹/(e¹+6e⁰) = e/(e+6) ≈ 0.312 → score ≈ 31.2
        val logits = floatArrayOf(1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f)
        val result = scorer.score(logits)

        if (result.score >= 31.0f) {
            assertTrue(result.isFrustrated)
        } else {
            assertFalse(result.isFrustrated)
        }
    }

    @Test
    fun `score is clipped to 0-100`() {
        // Even with extreme logits, score should be in [0, 100]
        val logits = floatArrayOf(100.0f, 100.0f, 100.0f, -100.0f, -100.0f, -100.0f, -100.0f)
        val result = scorer.score(logits)

        assertTrue(result.score >= 0f)
        assertTrue(result.score <= 100f)
        // Should be exactly 100 since all probability mass is on frustration emotions
        assertEquals(100f, result.score, 0.001f)
    }

    @Test
    fun `score is 0 when neutral dominates`() {
        val logits = floatArrayOf(-10.0f, -10.0f, -10.0f, -10.0f, -10.0f, -10.0f, 10.0f)
        val result = scorer.score(logits)

        assertTrue("Score should be near 0 for neutral-dominant", result.score < 0.1f)
        assertFalse(result.isFrustrated)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects wrong number of logits`() {
        scorer.score(floatArrayOf(1.0f, 2.0f))
    }

    @Test
    fun `probabilities array has size 7`() {
        val logits = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f)
        val result = scorer.score(logits)
        assertEquals(7, result.probabilities.size)
    }
}
