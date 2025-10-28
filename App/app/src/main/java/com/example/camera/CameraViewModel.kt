package com.example.camera

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.util.Log

/**
 * ViewModel for managing camera screen state and navigation.
 * Handles screen transitions and object detection state management.
 */
class CameraViewModel : ViewModel() {
    companion object {
        private const val TAG = "CameraViewModel"
        private const val NAVIGATION_DELAY_MS = 300L
    }

    // Screen state management
    private val _currentScreen = mutableStateOf<ScreenState>(ScreenState.Camera)
    val currentScreen: State<ScreenState> = _currentScreen

    // Captured image URI for preview screen
    private val _capturedImageUri = mutableStateOf<String?>(null)
    val capturedImageUri: State<String?> = _capturedImageUri

    // Navigation state to prevent rapid screen switches
    private val _isNavigating = mutableStateOf(false)
    val isNavigating: State<Boolean> = _isNavigating

    // Object detection results
    private val _detectedObjects = mutableStateOf<List<HighlightAnalysis.UiBox>>(emptyList())
    val detectedObjects: State<List<HighlightAnalysis.UiBox>> = _detectedObjects

    // Transformation matrix for object detection coordinates
    private val _analyzerToBuffer = mutableStateOf(android.graphics.Matrix())
    val analyzerToBuffer: State<android.graphics.Matrix> = _analyzerToBuffer

    /**
     * Safely navigates to the camera screen.
     * Activates object detection and clears preview data.
     */
    fun navigateToCamera() {
        if (_isNavigating.value) {
            Log.d(TAG, "Navigation already in progress, ignoring request")
            return
        }

        viewModelScope.launch {
            try {
                _isNavigating.value = true
                Log.d(TAG, "Navigating to Camera screen")

                // Activate the detector when returning to camera
                HighlightAnalysis.DetectorHolder.activate()

                // Clear previous image URI
                _capturedImageUri.value = null

                // Switch to camera screen
                _currentScreen.value = ScreenState.Camera

                // Short delay to prevent rapid clicks
                delay(NAVIGATION_DELAY_MS)

            } catch (e: Exception) {
                Log.e(TAG, "Error navigating to camera", e)
            } finally {
                _isNavigating.value = false
            }
        }
    }

    /**
     * Safely navigates to the photo preview screen.
     * Deactivates object detection and sets the image URI.
     *
     * @param uri The URI of the captured photo
     */
    fun navigateToPreview(uri: String) {
        if (_isNavigating.value) {
            Log.d(TAG, "Navigation already in progress, ignoring request")
            return
        }

        viewModelScope.launch {
            try {
                _isNavigating.value = true
                Log.d(TAG, "Navigating to Preview screen with URI: $uri")

                // Deactivate the detector when entering preview (CRITICAL: prevents crashes)
                HighlightAnalysis.DetectorHolder.deactivate()

                // Clear detection results
                clearDetectionData()

                // Set the image URI for preview
                _capturedImageUri.value = uri

                // Switch to preview screen
                _currentScreen.value = ScreenState.PhotoPreview

                // Short delay to prevent rapid clicks
                delay(NAVIGATION_DELAY_MS)

            } catch (e: Exception) {
                Log.e(TAG, "Error navigating to preview", e)
            } finally {
                _isNavigating.value = false
            }
        }
    }

    /**
     * Updates the detected objects and transformation matrix.
     * Only updates if detector is active and we're on camera screen.
     *
     * @param objects List of detected objects
     * @param transformMatrix Coordinate transformation matrix
     */
    fun updateDetectedObjects(objects: List<HighlightAnalysis.UiBox>, transformMatrix: android.graphics.Matrix) {
        if (HighlightAnalysis.DetectorHolder.isActive() && _currentScreen.value == ScreenState.Camera) {
            _detectedObjects.value = objects
            _analyzerToBuffer.value = transformMatrix
        }
    }

    /**
     * Clears all detection data and results.
     */
    fun clearDetectionData() {
        _detectedObjects.value = emptyList()
        _analyzerToBuffer.value = android.graphics.Matrix()
    }

    /**
     * Called when ViewModel is being destroyed.
     * Releases the detector and clears all data.
     */
    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel cleared, releasing detector")
        HighlightAnalysis.DetectorHolder.release()
        clearDetectionData()
    }
}