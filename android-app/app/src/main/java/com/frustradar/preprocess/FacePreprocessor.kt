package com.frustradar.preprocess

import android.graphics.Bitmap

/**
 * Converts a cropped face Bitmap into the float tensor expected by
 * [com.frustradar.ml.FacialInference].
 *
 * Normalization (ML-D4 resolved from HuggingFace `preprocessor_config.json`):
 *   pixel[0-255] → ×(1/255) → [0,1] → (x - 0.5) / 0.5 → [-1, +1]
 *
 * Output: NCHW [1, 3, 224, 224] = FloatArray of size 150528.
 */
class FacePreprocessor {

    /**
     * @param faceBitmap Cropped face bitmap (any size — will be resized to 224×224).
     * @return FloatArray of size 150528 in NCHW layout, normalized to [-1, +1].
     */
    fun preprocess(faceBitmap: Bitmap): FloatArray {
        // Resize to 224×224.
        val resized = if (faceBitmap.width == SIZE && faceBitmap.height == SIZE) {
            faceBitmap
        } else {
            Bitmap.createScaledBitmap(faceBitmap, SIZE, SIZE, true)
        }

        val pixels = IntArray(SIZE * SIZE)
        resized.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)

        // NCHW layout: channel-first — [R_plane, G_plane, B_plane]
        val tensor = FloatArray(3 * SIZE * SIZE)
        val planeSize = SIZE * SIZE

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = ((pixel shr 16) and 0xFF).toFloat()
            val g = ((pixel shr 8) and 0xFF).toFloat()
            val b = (pixel and 0xFF).toFloat()

            // Rescale 0–255 → 0–1, then normalize: (x - 0.5) / 0.5 = 2x - 1
            tensor[i] = r / 255f * 2f - 1f                      // R plane
            tensor[planeSize + i] = g / 255f * 2f - 1f           // G plane
            tensor[2 * planeSize + i] = b / 255f * 2f - 1f       // B plane
        }

        // Recycle the intermediate bitmap if we created one.
        if (resized !== faceBitmap) {
            resized.recycle()
        }

        return tensor
    }

    companion object {
        /** Target resolution per facial_input_spec.json. */
        private const val SIZE = 224
    }
}
