package com.frustradar.ml

import android.content.Context
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Log

/**
 * Hilt @Singleton managing OrtEnvironment, loading the manifest via Gson, 
 * verifying SHA-256/sizes, creating OnnxSessions, and exposing inference wrappers.
 */
@Singleton
class ModelProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val environment: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val gson = Gson()

    var facialInference: FacialInference? = null
        private set
    var voiceInference: VoiceInference? = null
        private set
    var motionModel: MotionModel? = null
        private set

    private var facialSession: OnnxSession? = null
    private var voiceSession: OnnxSession? = null

    init {
        loadModels()
    }

    private fun loadModels() {
        try {
            val manifestJson = context.assets.open("ml-models/ml_manifest.json").bufferedReader().use { it.readText() }
            val manifest = gson.fromJson(manifestJson, MlManifestDto::class.java)

            // Facial
            manifest.modalities.facial?.let { config ->
                if (config.present) {
                    facialSession = loadOnnxSession(config)
                    facialSession?.let {
                        facialInference = FacialInference(it)
                    }
                }
            }

            // Voice
            manifest.modalities.voice?.let { config ->
                if (config.present) {
                    voiceSession = loadOnnxSession(config)
                    voiceSession?.let {
                        voiceInference = VoiceInference(it)
                    }
                }
            }

            // Motion
            manifest.modalities.motion?.let { config ->
                if (config.present) {
                    motionModel = loadMotionModel(config)
                }
            }

        } catch (e: Exception) {
            Log.e("ModelProvider", "Failed to parse ml_manifest.json", e)
        }
    }

    private fun loadOnnxSession(config: ModalityConfigDto): OnnxSession? {
        val assetPath = "ml-models/${config.artifact}"
        return try {
            val modelBytes = verifyAndReadArtifact(assetPath, config.sizeBytes, config.sha256)
            if (modelBytes != null) {
                val session = environment.createSession(modelBytes, OrtSession.SessionOptions())
                OnnxSession(environment, session)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("ModelProvider", "Failed to load ONNX session for $assetPath", e)
            null
        }
    }

    private fun loadMotionModel(config: ModalityConfigDto): MotionModel? {
        val assetPath = "ml-models/${config.artifact}"
        return try {
            val jsonBytes = verifyAndReadArtifact(assetPath, config.sizeBytes, config.sha256)
            if (jsonBytes != null) {
                val jsonString = String(jsonBytes, Charsets.UTF_8)
                MotionModel(jsonString)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("ModelProvider", "Failed to load Motion model for $assetPath", e)
            null
        }
    }

    /**
     * Reads the asset file, verifies size and SHA-256. 
     * Returns ByteArray if valid, null if invalid.
     */
    private fun verifyAndReadArtifact(path: String, expectedSize: Long, expectedSha256: String): ByteArray? {
        val bytes = try {
            context.assets.open(path).use { it.readBytes() }
        } catch (e: Exception) {
            Log.w("ModelProvider", "Artifact not found at $path")
            return null
        }

        if (bytes.size.toLong() != expectedSize) {
            Log.w("ModelProvider", "Size mismatch for $path. Expected $expectedSize, got ${bytes.size}")
            return null
        }

        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(bytes)
        val computedSha256 = hashBytes.joinToString("") { "%02X".format(it) }

        if (!computedSha256.equals(expectedSha256, ignoreCase = true)) {
            Log.w("ModelProvider", "SHA-256 mismatch for $path. Expected $expectedSha256, got $computedSha256")
            return null
        }

        return bytes
    }

    fun close() {
        facialSession?.close()
        voiceSession?.close()
        environment.close()
    }
}
