package com.example.camera

import android.content.Context
import android.util.Log

/**
 * Simplified detector manager that uses HighlightAnalysis.DetectorHolder
 * This provides a unified interface for detector state management
 */
object DetectorManager {
    private const val TAG = "DetectorManager"

    /**
     * Gets the existing detector or creates a new one if none exists.
     *
     * @param context Application context for model loading
     * @return ObjectDetector instance or null if creation fails
     */
    @Synchronized
    fun getOrCreateDetector(context: Context) = HighlightAnalysis.DetectorHolder.get(context)

    /**
     * Activates the detector to start processing images.
     * Call this when entering camera screen.
     *
     * @return true if detector was successfully activated
     */
    @Synchronized
    fun activateDetector(): Boolean {
        HighlightAnalysis.DetectorHolder.activate()
        Log.d(TAG, "Detector activated via DetectorManager")
        return true
    }

    /**
     * Deactivates the detector to stop processing images.
     * Call this when leaving camera screen (e.g., entering preview).
     */
    @Synchronized
    fun deactivateDetector() {
        HighlightAnalysis.DetectorHolder.deactivate()
        Log.d(TAG, "Detector deactivated via DetectorManager")
    }

    /**
     * Checks if the detector is currently active and should process images.
     *
     * @return true if detector is active, false otherwise
     */
    fun isDetectorActive(): Boolean {
        return HighlightAnalysis.DetectorHolder.isActive()
    }

    /**
     * Releases the detector instance and clears all references.
     * Call this only when the app is being destroyed.
     */
    @Synchronized
    fun releaseDetector() {
        HighlightAnalysis.DetectorHolder.release()
        Log.d(TAG, "Detector released via DetectorManager")
    }
}