package com.frustradar.scoring

import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Computes acoustic features from raw (unnormalized) PCM audio.
 *
 * Per `voice_scoring_config.json`:
 *   - Features: rms_energy, baseline_relative_energy, db_jump, onset_strength
 *   - acoustic_score = clamp((dB_above_baseline / 30) × 100, 0, 100)
 *   - Spike: energy > 15 dB above baseline
 *
 * Maintains a rolling baseline of the last [BASELINE_WINDOW_COUNT] scoring windows.
 *
 * > **RECONCILIATION CONSTANT 3 (ML-D6):** The baseline window count (10 windows / ~20s)
 * > is NOT sourced from the shipped mobile-package config (unavailable, blocker B3).
 * > It is a documented reconciliation constant. Must be verified/replaced when B3 is provided.
 */
class AcousticAnalyzer {

    private val baselineBuffer = ArrayDeque<Float>(BASELINE_WINDOW_COUNT)

    /**
     * Result of acoustic analysis.
     *
     * @param score            0–100 acoustic frustration score.
     * @param rmsEnergy        Root-mean-square energy of the window.
     * @param dbAboveBaseline  dB above rolling baseline (≥0).
     * @param spikeDetected    True if dB_above_baseline > 15.
     */
    data class AcousticResult(
        val score: Float,
        val rmsEnergy: Float,
        val dbAboveBaseline: Float,
        val spikeDetected: Boolean
    )

    /**
     * Analyze a raw PCM window and produce an acoustic score.
     *
     * @param pcmSamples Raw (unnormalized) 16 kHz mono PCM samples.
     * @return [AcousticResult] with score in 0–100.
     */
    fun analyze(pcmSamples: FloatArray): AcousticResult {
        require(pcmSamples.isNotEmpty()) { "PCM samples must not be empty" }

        val rms = computeRms(pcmSamples)
        val rmsDb = amplitudeToDb(rms)

        // Baseline: mean dB of past windows, or current dB if no history yet.
        val baselineDb = if (baselineBuffer.isEmpty()) rmsDb else baselineBuffer.average().toFloat()
        val dbAboveBaseline = (rmsDb - baselineDb).coerceAtLeast(0f)

        // Update rolling baseline buffer.
        if (baselineBuffer.size >= BASELINE_WINDOW_COUNT) {
            baselineBuffer.removeFirst()
        }
        baselineBuffer.addLast(rmsDb)

        // Acoustic score formula per voice_scoring_config.json.
        val rawScore = (dbAboveBaseline / 30f) * 100f
        val score = rawScore.coerceIn(0f, 100f)

        val spikeDetected = dbAboveBaseline > SPIKE_THRESHOLD_DB

        return AcousticResult(
            score = score,
            rmsEnergy = rms,
            dbAboveBaseline = dbAboveBaseline,
            spikeDetected = spikeDetected
        )
    }

    /** Reset the rolling baseline (e.g. at session start). */
    fun reset() {
        baselineBuffer.clear()
    }

    private fun computeRms(samples: FloatArray): Float {
        var sumSq = 0.0
        for (s in samples) {
            sumSq += s * s
        }
        return sqrt(sumSq / samples.size).toFloat()
    }

    /**
     * Convert RMS amplitude to decibels. Uses 20·log10(amplitude) with a floor
     * to avoid log(0).
     */
    private fun amplitudeToDb(amplitude: Float): Float {
        val safeAmplitude = amplitude.coerceAtLeast(AMPLITUDE_FLOOR)
        return (20.0 * log10(safeAmplitude.toDouble())).toFloat()
    }

    companion object {
        /** Spike detection threshold per voice_scoring_config.json. */
        const val SPIKE_THRESHOLD_DB = 15f

        /**
         * Rolling baseline window count.
         * RECONCILIATION CONSTANT 3 (ML-D6): NOT from shipped config, flagged for B3.
         */
        const val BASELINE_WINDOW_COUNT = 10

        /** Floor to prevent log10(0). */
        private const val AMPLITUDE_FLOOR = 1e-10f
    }
}
