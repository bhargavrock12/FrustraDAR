package com.frustradar.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer

/**
 * Wrapper around ai.onnxruntime.OrtSession.
 * Manages tensor lifecycle (creation, memory release) and provides a safe run() execution layer.
 */
class OnnxSession(
    private val environment: OrtEnvironment,
    private val session: OrtSession
) {

    /**
     * Executes the ONNX model using the provided input array and tensor metadata.
     * 
     * @param inputName The name of the input tensor (e.g., "pixel_values").
     * @param inputShape The shape of the input tensor (e.g., [1, 3, 224, 224]).
     * @param inputValues The flattened float array data.
     * @return The raw float array output from the model (e.g., logits).
     */
    fun run(inputName: String, inputShape: LongArray, inputValues: FloatArray): FloatArray {
        var tensor: OnnxTensor? = null
        var result: OrtSession.Result? = null
        return try {
            val floatBuffer = FloatBuffer.wrap(inputValues)
            tensor = OnnxTensor.createTensor(environment, floatBuffer, inputShape)
            
            val inputs = mapOf(inputName to tensor)
            result = session.run(inputs)
            
            // Assume the first output is the one we want, and it's a 2D float array [1, num_classes]
            val outputTensor = result.get(0) as OnnxTensor
            val outputData = outputTensor.floatBuffer.array()
            outputData
        } finally {
            tensor?.close()
            result?.close()
        }
    }

    /**
     * Closes the OrtSession to release JNI resources.
     */
    fun close() {
        session.close()
    }
}
