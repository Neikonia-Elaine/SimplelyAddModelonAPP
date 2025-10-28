package com.example.camera

import android.content.Context
import android.graphics.RectF
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import org.tensorflow.lite.task.core.BaseOptions
import androidx.camera.core.ImageProxy
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import android.util.Log

/**
 * Utility class for ML object detection using EfficientDet-Lite4
 */
object HighlightAnalysis {
    private const val TAG = "HighlightAnalysis"

    /**
     * Data class to hold detected objects
     */
    data class UiBox(
        val bbox: RectF,
        val label: String,
        val score: Float
    )

    /**
     * Singleton holder for ObjectDetector to avoid recreating the model
     * Added thread safety and activation control to prevent crashes
     */
    object DetectorHolder {
        @Volatile
        private var detector: ObjectDetector? = null

        // Control whether detection should be active
        private val isActive = AtomicBoolean(false)

        // Prevent multiple threads from initializing simultaneously
        private val isInitializing = AtomicBoolean(false)

        fun get(context: Context): ObjectDetector? {
            // Return existing detector if available
            detector?.let { return it }

            // Prevent multiple threads from initializing simultaneously
            if (!isInitializing.compareAndSet(false, true)) {
                Log.d(TAG, "Detector initialization in progress, waiting...")
                // Wait for other thread to complete initialization
                while (isInitializing.get() && detector == null) {
                    Thread.sleep(50)
                }
                return detector
            }

            return try {
                detector ?: synchronized(this) {
                    detector ?: build(context).also {
                        detector = it
                        Log.d(TAG, "EfficientDet-Lite4 detector created successfully")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create detector", e)
                null
            } finally {
                isInitializing.set(false)
            }
        }

        private fun build(context: Context): ObjectDetector {
            val baseOptions = BaseOptions.builder()
                .setNumThreads(4)
                .build()

            val options = ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(baseOptions)
                .setMaxResults(5)
                .setScoreThreshold(0.35f)
                .build()

            return ObjectDetector.createFromFileAndOptions(
                context,
                "efficientdet_lite4_metadata.tflite", // Your original model
                options
            )
        }

        /**
         * Activate detector for processing
         */
        fun activate() {
            val wasActive = isActive.getAndSet(true)
            Log.d(TAG, "Detector activated, was previously active: $wasActive")
        }

        /**
         * Deactivate detector to stop processing
         */
        fun deactivate() {
            val wasActive = isActive.getAndSet(false)
            Log.d(TAG, "Detector deactivated, was previously active: $wasActive")
        }

        /**
         * Check if detector should process images
         */
        fun isActive(): Boolean {
            return isActive.get()
        }

        /**
         * Release detector resources
         */
        fun release() {
            synchronized(this) {
                isActive.set(false)
                detector = null
                Log.d(TAG, "Detector released")
            }
        }
    }

    /**
     * Creates an ImageAnalysis use case that detects objects in real-time
     * Added thread safety and proper resource management
     *
     * @param context Application context for model loading
     * @param executor The executor to run analysis on
     * @param onDetectionResult Callback with detected objects and transform matrix
     * @return Configured ImageAnalysis instance
     */
    @OptIn(ExperimentalGetImage::class)
    fun createObjectDetectionAnalyzer(
        context: Context,
        executor: Executor,
        onDetectionResult: (List<UiBox>, android.graphics.Matrix) -> Unit
    ): ImageAnalysis {

        // Thread-safe processing flag
        val isProcessing = AtomicBoolean(false)

        return ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setOutputImageRotationEnabled(false)
            .build()
            .apply {
                setAnalyzer(executor) { imageProxy ->
                    // Check if detector is active before processing
                    if (!DetectorHolder.isActive()) {
                        Log.d(TAG, "Detector not active, skipping frame")
                        imageProxy.close()
                        return@setAnalyzer
                    }

                    // Prevent multiple threads from processing simultaneously
                    if (!isProcessing.compareAndSet(false, true)) {
                        Log.d(TAG, "Analysis already in progress, skipping frame")
                        imageProxy.close()
                        return@setAnalyzer
                    }

                    val mediaImg = imageProxy.image
                    if (mediaImg == null) {
                        isProcessing.set(false)
                        imageProxy.close()
                        return@setAnalyzer
                    }

                    try {
                        // Double-check detector state (may have been deactivated during queue time)
                        if (!DetectorHolder.isActive()) {
                            Log.d(TAG, "Detector deactivated during execution, aborting")
                            return@setAnalyzer
                        }

                        // Convert to bitmap
                        val bitmap = imageProxy.toBitmap()

                        val detectedObjects = mutableListOf<UiBox>()

                        // Get detector and run inference
                        val detector = DetectorHolder.get(context)
                        if (detector == null) {
                            Log.w(TAG, "Detector not available, skipping analysis")
                            // Send empty results on detector unavailable
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                if (DetectorHolder.isActive()) {
                                    onDetectionResult(emptyList(), android.graphics.Matrix())
                                }
                            }
                            return@setAnalyzer
                        }

                        val tensorImage = TensorImage.fromBitmap(bitmap)
                        val results = detector.detect(tensorImage)

                        // Process detection results
                        results.forEach { detection ->
                            val category = detection.categories.firstOrNull()
                            if (category != null) {
                                val label = category.label
                                val score = category.score
                                val boundingBox = detection.boundingBox

                                detectedObjects.add(
                                    UiBox(
                                        bbox = RectF(boundingBox),
                                        label = label,
                                        score = score
                                    )
                                )
                            }
                        }

                        // Get transformation matrix for coordinate mapping
                        val transformMatrix = imageProxy.imageInfo.sensorToBufferTransformMatrix

                        Log.d(TAG, "Detection completed, found ${detectedObjects.size} objects")

                        // Send results back via callback (ensure on main thread and detector still active)
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            try {
                                if (DetectorHolder.isActive()) {
                                    onDetectionResult(detectedObjects, transformMatrix)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error in detection result callback", e)
                            }
                        }

                    } catch (exception: Exception) {
                        // Log error but don't crash the app
                        Log.e(TAG, "Error during object detection", exception)

                        // Send empty results on error (ensure on main thread)
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            try {
                                if (DetectorHolder.isActive()) {
                                    onDetectionResult(emptyList(), android.graphics.Matrix())
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error in error callback", e)
                            }
                        }
                    } finally {
                        // Always clean up resources and reset processing flag
                        try {
                            imageProxy.close()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error closing imageProxy", e)
                        }
                        isProcessing.set(false)
                    }
                }
            }
    }
}