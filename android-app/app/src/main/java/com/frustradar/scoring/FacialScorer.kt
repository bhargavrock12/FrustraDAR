package com.frustradar.scoring

import kotlin.math.exp

/**
 * Scores facial frustration from the 7 raw logits produced by
 * [com.frustradar.ml.FacialInference].
 *
 * Per `facial_scoring_config.json` (FD-21):
 *   - Emotion labels ordered: [angry, disgust, fear, happy, sad, surprise, neutral]
 *   - Softmax applied to convert logits → probabilities
 *   - facial_score = (P_angry + P_disgust + P_fear) × 100, clipped 0–100
 *   - Threshold 31.0: ≥ 31 → frustrated (display-only boolean, not used by fusion)
 */
class FacialScorer {

    /**
     * Result of facial scoring.
     *
     * @param score           Continuous 0–100 score feeding fusion.
     * @param probabilities   7 softmax probabilities in emotion_labels_ordered order.
     * @param isFrustrated    Display-only boolean: score ≥ 31.
     */
    data class FacialResult(
        val score: Float,
        val probabilities: FloatArray,
        val isFrustrated: Boolean
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is FacialResult) return false
            return score == other.score &&
                probabilities.contentEquals(other.probabilities) &&
                isFrustrated == other.isFrustrated
        }

        override fun hashCode(): Int {
            var result = score.hashCode()
            result = 31 * result + probabilities.contentHashCode()
            result = 31 * result + isFrustrated.hashCode()
            return result
        }
    }

    /**
     * @param logits Raw 7-element logit array from FacialInference.infer().
     *   Order: [angry, disgust, fear, happy, sad, surprise, neutral].
     * @return [FacialResult] with score in 0–100.
     */
    fun score(logits: FloatArray): FacialResult {
        require(logits.size == 7) { "Expected 7 logits, got ${logits.size}" }

        val probabilities = softmax(logits)

        // facial_score = (P_angry + P_disgust + P_fear) × 100
        val rawScore = (probabilities[IDX_ANGRY] + probabilities[IDX_DISGUST] + probabilities[IDX_FEAR]) * 100f
        val clippedScore = rawScore.coerceIn(0f, 100f)

        return FacialResult(
            score = clippedScore,
            probabilities = probabilities,
            isFrustrated = clippedScore >= FRUSTRATION_THRESHOLD
        )
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val maxLogit = logits.max()
        val exps = FloatArray(logits.size) { exp((logits[it] - maxLogit).toDouble()).toFloat() }
        val sumExps = exps.sum()
        return FloatArray(logits.size) { exps[it] / sumExps }
    }

    companion object {
        /** Indices into emotion_labels_ordered. */
        private const val IDX_ANGRY = 0
        private const val IDX_DISGUST = 1
        private const val IDX_FEAR = 2

        /** Display-only classification threshold (per facial_scoring_config.json). */
        const val FRUSTRATION_THRESHOLD = 31.0f
    }
}
