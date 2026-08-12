package com.frustradar.ml

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Executes the Facial ONNX model.
 * Input: [1, 3, 224, 224] FloatArray
 * Output: FloatArray of size 7 (raw logits)
 */
class FacialInference @Inject constructor(
    private val onnxSession: OnnxSession
) {
    suspend fun infer(pixelValues: FloatArray): FloatArray = withContext(Dispatchers.Default) {
        require(pixelValues.size == 3 * 224 * 224) { "Facial inference requires exactly 150528 elements." }
        
        val shape = longArrayOf(1, 3, 224, 224)
        val result = onnxSession.run("pixel_values", shape, pixelValues)
        
        require(result.size == 7) { "Expected facial output size of 7, got ${result.size}" }
        result
    }
}
