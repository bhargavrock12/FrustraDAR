package com.frustradar.scoring

import kotlin.math.exp

/**
 * Scores voice frustration from the 6 raw logits produced by
 * [com.frustradar.ml.VoiceInference] and an [AcousticAnalyzer.AcousticResult].
 *
 * Per `voice_scoring_config.json` (FD-22):
 *   - Emotion labels ordered: [anger, disgust, fear, happy, neutral, sad]
 *   - Softmax → emotion_score = 100 × (0.60·P_anger + 0.15·P_disgust + 0.15·P_fear + 0.10·P_sad)
 *   - voice_score = clip(0.70·emotion_score + 0.30·acoustic_score, 0, 100)
 *   - Moderation (applied after the 0.70/0.30 blend):
 *       • P_happy > 0.60 AND P_anger < 0.25 → ×0.75
 *       • P_neutral > 0.70 AND P_anger < 0.20 → ×0.70
 *
 * Uploaded to backend as `audio_score`.
 */
class VoiceScorer {

    /**
     * Result of voice scoring.
     *
     * @param score          Continuous 0–100 voice score (= backend audio_score).
     * @param emotionScore   0–100 emotion-only score before acoustic blend.
     * @param probabilities  6 softmax probabilities in emotion_classes_ordered order.
     * @param moderationApplied Name of moderation rule applied, or null.
     */
    data class VoiceResult(
        val score: Float,
        val emotionScore: Float,
        val probabilities: FloatArray,
        val moderationApplied: String?
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is VoiceResult) return false
            return score == other.score &&
                emotionScore == other.emotionScore &&
                probabilities.contentEquals(other.probabilities) &&
                moderationApplied == other.moderationApplied
        }

        override fun hashCode(): Int {
            var result = score.hashCode()
            result = 31 * result + emotionScore.hashCode()
            result = 31 * result + probabilities.contentHashCode()
            result = 31 * result + (moderationApplied?.hashCode() ?: 0)
            return result
        }
    }

    /**
     * @param logits         Raw 6-element logit array from VoiceInference.infer().
     *   Order: [anger, disgust, fear, happy, neutral, sad].
     * @param acousticResult Result from [AcousticAnalyzer.analyze].
     * @return [VoiceResult] with score in 0–100.
     */
    fun score(logits: FloatArray, acousticResult: AcousticAnalyzer.AcousticResult): VoiceResult {
        require(logits.size == 6) { "Expected 6 logits, got ${logits.size}" }

        val probabilities = softmax(logits)

        // emotion_score = 100 × (0.60·P_anger + 0.15·P_disgust + 0.15·P_fear + 0.10·P_sad)
        val emotionScore = 100f * (
            WEIGHT_ANGER * probabilities[IDX_ANGER] +
            WEIGHT_DISGUST * probabilities[IDX_DISGUST] +
            WEIGHT_FEAR * probabilities[IDX_FEAR] +
            WEIGHT_SAD * probabilities[IDX_SAD]
        )

        // voice_score = 0.70·emotion_score + 0.30·acoustic_score
        var voiceScore = EMOTION_WEIGHT * emotionScore + ACOUSTIC_WEIGHT * acousticResult.score

        // Moderation (applied after the blend).
        var moderationApplied: String? = null
        if (probabilities[IDX_HAPPY] > 0.60f && probabilities[IDX_ANGER] < 0.25f) {
            voiceScore *= 0.75f
            moderationApplied = "happy_suppression"
        } else if (probabilities[IDX_NEUTRAL] > 0.70f && probabilities[IDX_ANGER] < 0.20f) {
            voiceScore *= 0.70f
            moderationApplied = "neutral_suppression"
        }

        val clippedScore = voiceScore.coerceIn(0f, 100f)

        return VoiceResult(
            score = clippedScore,
            emotionScore = emotionScore,
            probabilities = probabilities,
            moderationApplied = moderationApplied
        )
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val maxLogit = logits.max()
        val exps = FloatArray(logits.size) { exp((logits[it] - maxLogit).toDouble()).toFloat() }
        val sumExps = exps.sum()
        return FloatArray(logits.size) { exps[it] / sumExps }
    }

    companion object {
        /** Indices into emotion_classes_ordered [anger, disgust, fear, happy, neutral, sad]. */
        private const val IDX_ANGER = 0
        private const val IDX_DISGUST = 1
        private const val IDX_FEAR = 2
        private const val IDX_HAPPY = 3
        private const val IDX_NEUTRAL = 4
        private const val IDX_SAD = 5

        /** Emotion score weights per voice_scoring_config.json. */
        private const val WEIGHT_ANGER = 0.60f
        private const val WEIGHT_DISGUST = 0.15f
        private const val WEIGHT_FEAR = 0.15f
        private const val WEIGHT_SAD = 0.10f

        /** Blend weights per voice_scoring_config.json. */
        private const val EMOTION_WEIGHT = 0.70f
        private const val ACOUSTIC_WEIGHT = 0.30f
    }
}
