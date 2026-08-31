package com.frustradar.scoring

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for [VoiceScorer].
 *
 * Covers:
 * - Softmax on 6 logits
 * - Emotion score weighted sum (0.60/0.15/0.15/0.10)
 * - Voice score = 0.70×emotion + 0.30×acoustic
 * - Moderation: happy>0.60 ∧ anger<0.25 → ×0.75
 * - Moderation: neutral>0.70 ∧ anger<0.20 → ×0.70
 * - Clipping to 0–100
 */
class VoiceScorerTest {

    private val scorer = VoiceScorer()

    private fun acousticResult(score: Float = 50f) = AcousticAnalyzer.AcousticResult(
        score = score,
        rmsEnergy = 0.1f,
        dbAboveBaseline = 15f,
        spikeDetected = false
    )

    @Test
    fun `softmax produces valid probabilities summing to 1`() {
        val logits = floatArrayOf(2.0f, 1.0f, 0.5f, 0.1f, -1.0f, -0.5f)
        val result = scorer.score(logits, acousticResult())
        val probSum = result.probabilities.sum()
        assertEquals(1.0f, probSum, 0.001f)
        assertEquals(6, result.probabilities.size)
    }

    @Test
    fun `emotion score formula - anger dominant`() {
        // anger(0) has high logit, rest low
        val logits = floatArrayOf(10.0f, -10.0f, -10.0f, -10.0f, -10.0f, -10.0f)
        val result = scorer.score(logits, acousticResult(score = 0f))

        // P_anger ≈ 1.0, emotion_score ≈ 100 × 0.60 = 60
        // voice_score = 0.70 × 60 + 0.30 × 0 = 42
        assertEquals(60f, result.emotionScore, 1f)
        assertEquals(42f, result.score, 1f)
    }

    @Test
    fun `emotion score formula - mixed negative emotions`() {
        // All equal logits → uniform probabilities → each P = 1/6
        val logits = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f)
        val result = scorer.score(logits, acousticResult(score = 0f))

        // emotion_score = 100 × (0.60/6 + 0.15/6 + 0.15/6 + 0.10/6)
        // = 100 × (1.0/6) = 16.67
        val expectedEmotion = 100f * (0.60f + 0.15f + 0.15f + 0.10f) / 6f
        assertEquals(expectedEmotion, result.emotionScore, 0.5f)
    }

    @Test
    fun `voice score blends emotion and acoustic`() {
        val logits = floatArrayOf(10.0f, -10.0f, -10.0f, -10.0f, -10.0f, -10.0f)
        val result = scorer.score(logits, acousticResult(score = 100f))

        // emotion_score ≈ 60, voice_score = 0.70 × 60 + 0.30 × 100 = 72
        assertEquals(72f, result.score, 2f)
    }

    @Test
    fun `moderation - happy suppression`() {
        // happy(3) dominant → P_happy > 0.60, P_anger < 0.25
        val logits = floatArrayOf(-5.0f, -5.0f, -5.0f, 10.0f, -5.0f, -5.0f)
        val result = scorer.score(logits, acousticResult(score = 50f))

        assertEquals("happy_suppression", result.moderationApplied)
        // Score should be reduced by ×0.75
        // Without moderation: 0.70 × (near 0) + 0.30 × 50 = ~15
        // With moderation: ~15 × 0.75 = ~11.25
        assertTrue(result.score < 15f)
    }

    @Test
    fun `moderation - neutral suppression`() {
        // neutral(4) dominant → P_neutral > 0.70, P_anger < 0.20
        val logits = floatArrayOf(-5.0f, -5.0f, -5.0f, -5.0f, 10.0f, -5.0f)
        val result = scorer.score(logits, acousticResult(score = 50f))

        assertEquals("neutral_suppression", result.moderationApplied)
    }

    @Test
    fun `no moderation when anger is high`() {
        // anger dominant → neither moderation rule applies
        val logits = floatArrayOf(10.0f, -5.0f, -5.0f, -5.0f, -5.0f, -5.0f)
        val result = scorer.score(logits, acousticResult(score = 50f))

        assertNull(result.moderationApplied)
    }

    @Test
    fun `score clipped to 0-100`() {
        val logits = floatArrayOf(100.0f, 100.0f, 100.0f, -100.0f, -100.0f, 100.0f)
        val result = scorer.score(logits, acousticResult(score = 100f))

        assertTrue(result.score >= 0f)
        assertTrue(result.score <= 100f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects wrong number of logits`() {
        scorer.score(floatArrayOf(1.0f), acousticResult())
    }

    @Test
    fun `zero acoustic score - only emotion contributes`() {
        val logits = floatArrayOf(10.0f, -10.0f, -10.0f, -10.0f, -10.0f, -10.0f)
        val result = scorer.score(logits, acousticResult(score = 0f))

        // voice_score = 0.70 × emotion_score + 0
        val expected = 0.70f * result.emotionScore
        assertEquals(expected, result.score, 0.5f)
    }
}
