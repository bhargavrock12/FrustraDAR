package com.frustradar.scoring

import android.graphics.Bitmap
import com.frustradar.fusion.FusionEngine
import com.frustradar.ml.FacialInference
import com.frustradar.ml.MotionModel
import com.frustradar.ml.VoiceInference
import com.frustradar.motion.ImuWindow
import com.frustradar.motion.MotionBaselineManager
import com.frustradar.motion.MotionFeatureExtractor
import com.frustradar.preprocess.AudioPreprocessor
import com.frustradar.preprocess.FaceDetector
import com.frustradar.preprocess.FacePreprocessor
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates the full per-window scoring flow:
 *   1. Accept available modality inputs (face frame, audio PCM, IMU window)
 *   2. Run preprocessing (FaceDetector → FacePreprocessor, AudioPreprocessor, MotionFeatureExtractor)
 *   3. Run inference (FacialInference, VoiceInference, MotionModel)
 *   4. Run scoring (FacialScorer, VoiceScorer + AcousticAnalyzer, MotionScorer)
 *   5. Run fusion (FusionEngine)
 *   6. Return ScoringResult
 *
 * Handles missing modalities gracefully: null input or failed inference → that modality is missing.
 *
 * **Not wired into SessionManager in Phase 5.** Phase 7 will inject it and call it
 * from the session pipeline loop once the sensor capture layer exists.
 */
@Singleton
class ScoringPipeline @Inject constructor(
    private val faceDetector: FaceDetector,
    private val facialInference: FacialInference?,
    private val voiceInference: VoiceInference?,
    private val motionModel: MotionModel?,
    private val motionBaselineManager: MotionBaselineManager
) {
    private val facePreprocessor = FacePreprocessor()
    private val audioPreprocessor = AudioPreprocessor()
    private val motionFeatureExtractor = MotionFeatureExtractor()
    private val facialScorer = FacialScorer()
    private val voiceScorer = VoiceScorer()
    private val acousticAnalyzer = AcousticAnalyzer()
    private val motionScorer = MotionScorer()
    private val fusionEngine = FusionEngine()

    private val formatter = DateTimeFormatter.ISO_INSTANT

    /**
     * Run the full scoring pipeline for a single window.
     *
     * @param faceFrame  Camera frame Bitmap, or null if camera unavailable.
     * @param audioPcm   Raw 16 kHz mono PCM float samples, or null if mic unavailable.
     * @param imuWindow  2-second IMU window, or null if sensors unavailable.
     * @return [ScoringResult], or null if all modalities are missing and fusion produces nothing.
     */
    suspend fun score(
        faceFrame: Bitmap?,
        audioPcm: FloatArray?,
        imuWindow: ImuWindow?
    ): ScoringResult? {
        val timestamp = formatter.format(Instant.now().atOffset(ZoneOffset.UTC))

        // ── Facial ──────────────────────────────────────────────────────────
        val facialResult = scoreFacial(faceFrame)

        // ── Voice ───────────────────────────────────────────────────────────
        val voiceResult = scoreVoice(audioPcm)

        // ── Motion ──────────────────────────────────────────────────────────
        val motionResult = scoreMotion(imuWindow)

        // ── Fusion ──────────────────────────────────────────────────────────
        val fusionResult = fusionEngine.fuse(
            facialScore = facialResult?.score,
            voiceScore = voiceResult?.score,
            motionScore = motionResult
        ) ?: return null  // All modalities missing.

        return ScoringResult(
            facialScore = facialResult?.score,
            voiceScore = voiceResult?.score,
            motionScore = motionResult,
            fusionScore = fusionResult.fusionScore,
            signalsUsed = fusionResult.signalsUsed,
            category = fusionResult.category,
            timestamp = timestamp
        )
    }

    private suspend fun scoreFacial(faceFrame: Bitmap?): FacialScorer.FacialResult? {
        if (faceFrame == null || facialInference == null) return null

        return try {
            val croppedFace = faceDetector.detectAndCrop(faceFrame) ?: return null
            val tensor = facePreprocessor.preprocess(croppedFace)
            val logits = facialInference.infer(tensor)
            facialScorer.score(logits)
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun scoreVoice(audioPcm: FloatArray?): VoiceScorer.VoiceResult? {
        if (audioPcm == null || voiceInference == null) return null

        return try {
            val preprocessed = audioPreprocessor.preprocess(audioPcm)
            val logits = voiceInference.infer(preprocessed.normalizedForModel)
            val acousticResult = acousticAnalyzer.analyze(preprocessed.rawForAcoustic)
            voiceScorer.score(logits, acousticResult)
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun scoreMotion(imuWindow: ImuWindow?): Float? {
        if (imuWindow == null || motionModel == null) return null
        val baseline = motionBaselineManager.getBaseline() ?: return null

        return try {
            val engineered = motionFeatureExtractor.extractEngineered(imuWindow)
            val ratios = motionFeatureExtractor.computeBaselineRatios(engineered, baseline)
            val probability = motionModel.infer(ratios)
            motionScorer.score(probability)
        } catch (_: Exception) {
            null
        }
    }

    /** Reset stateful components (e.g. at session start). */
    fun reset() {
        acousticAnalyzer.reset()
    }
}
