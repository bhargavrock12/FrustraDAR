package com.frustradar.preprocess

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects and crops the largest face from a camera frame using ML Kit.
 *
 * Per the authoritative architecture (08_ANDROID.md §1 tree and §2.3),
 * `FaceDetector.kt` belongs in `preprocess/`, not in `sensors/`.
 * Sensor capture (CameraX) is Phase 7.
 *
 * Returns null if no face is detected → facial modality is missing for the window.
 */
@Singleton
class FaceDetector @Inject constructor() {

    private val detector: FaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
    )

    /**
     * Detect faces in the given camera frame and return the largest cropped face,
     * or null if no face is detected.
     *
     * @param frame Camera frame as a Bitmap.
     * @return Cropped face Bitmap, or null.
     */
    suspend fun detectAndCrop(frame: Bitmap): Bitmap? {
        val inputImage = InputImage.fromBitmap(frame, 0)
        val faces = detector.process(inputImage).await()

        if (faces.isEmpty()) return null

        // Select the largest face by bounding-box area.
        val largestFace = faces.maxByOrNull {
            it.boundingBox.width() * it.boundingBox.height()
        } ?: return null

        val bounds = largestFace.boundingBox

        // Clamp bounding box to frame dimensions.
        val left = bounds.left.coerceAtLeast(0)
        val top = bounds.top.coerceAtLeast(0)
        val right = bounds.right.coerceAtMost(frame.width)
        val bottom = bounds.bottom.coerceAtMost(frame.height)
        val width = right - left
        val height = bottom - top

        if (width <= 0 || height <= 0) return null

        return Bitmap.createBitmap(frame, left, top, width, height)
    }
}
