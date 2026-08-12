package com.frustradar.ml

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Executes the Voice ONNX model.
 * Input: FloatArray of varying length (16kHz audio window)
 * Output: FloatArray of size 6 (raw logits)
 */
class VoiceInference @Inject constructor(
    private val onnxSession: OnnxSession
) {
    suspend fun infer(inputValues: FloatArray): FloatArray = withContext(Dispatchers.Default) {
        val shape = longArrayOf(1, inputValues.size.toLong())
        val result = onnxSession.run("input_values", shape, inputValues)
        
        require(result.size == 6) { "Expected voice output size of 6, got ${result.size}" }
        result
    }
}
