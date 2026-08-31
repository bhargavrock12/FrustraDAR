package com.frustradar.preprocess

/**
 * Preprocesses raw PCM audio for the Voice ONNX model.
 *
 * Normalization: per-utterance zero-mean, unit-variance.
 *
 * > **RECONCILIATION CONSTANT 1 (ML-D6):** This normalization type is NOT sourced
 * > from the shipped mobile-package config (unavailable, blocker B3). It is a documented
 * > reconciliation constant. Must be verified/replaced when B3 is provided.
 *
 * Also passes through the raw (unnormalized) PCM for [com.frustradar.scoring.AcousticAnalyzer],
 * which needs unnormalized amplitude data.
 */
class AudioPreprocessor {

    /**
     * @param rawPcm Raw 16 kHz mono PCM samples as float values.
     * @return [AudioPreprocessResult] containing both normalized and raw PCM.
     */
    fun preprocess(rawPcm: FloatArray): AudioPreprocessResult {
        require(rawPcm.isNotEmpty()) { "Raw PCM must not be empty" }

        // Per-utterance zero-mean, unit-variance normalization.
        val mean = rawPcm.average().toFloat()
        var sumSq = 0.0
        for (sample in rawPcm) {
            val diff = sample - mean
            sumSq += diff * diff
        }
        val std = kotlin.math.sqrt(sumSq / rawPcm.size).toFloat()
        val safeDenominator = if (std < EPSILON) EPSILON else std

        val normalized = FloatArray(rawPcm.size) { i ->
            (rawPcm[i] - mean) / safeDenominator
        }

        return AudioPreprocessResult(
            normalizedForModel = normalized,
            rawForAcoustic = rawPcm.copyOf()
        )
    }

    companion object {
        /** Minimum std to prevent division-by-zero on silent audio. */
        private const val EPSILON = 1e-7f
    }
}

/**
 * Result of audio preprocessing.
 *
 * @param normalizedForModel Zero-mean/unit-variance normalized PCM for VoiceInference.
 * @param rawForAcoustic     Unnormalized raw PCM for AcousticAnalyzer.
 */
data class AudioPreprocessResult(
    val normalizedForModel: FloatArray,
    val rawForAcoustic: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioPreprocessResult) return false
        return normalizedForModel.contentEquals(other.normalizedForModel) &&
            rawForAcoustic.contentEquals(other.rawForAcoustic)
    }

    override fun hashCode(): Int {
        var result = normalizedForModel.contentHashCode()
        result = 31 * result + rawForAcoustic.contentHashCode()
        return result
    }
}
