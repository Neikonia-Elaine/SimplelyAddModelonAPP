package com.example.camera

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA
import androidx.camera.core.CameraSelector.DEFAULT_FRONT_CAMERA
import androidx.camera.viewfinder.compose.MutableCoordinateTransformer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.setFrom
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.awaitCancellation
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Main camera screen composable that handles screen navigation.
 * Uses ViewModel for state management and safe navigation.
 *
 * @param onRequestPermission Callback for requesting camera permissions
 * @param username Username of the logged-in user or "Guest"
 * @param viewModel CameraViewModel instance for state management
 */
@Composable
fun CameraScreen(
    onRequestPermission: () -> Unit,
    username: String = "Guest",
    token: String = "",
    isLoggedIn: Boolean = false,
    onOpenGallery: () -> Unit = {},
    viewModel: CameraViewModel = viewModel()
) {
    val currentScreen by viewModel.currentScreen
    val capturedImageUri by viewModel.capturedImageUri
    val isNavigating by viewModel.isNavigating

    // Log screen changes for debugging
    LaunchedEffect(currentScreen) {
        Log.d("CameraScreen", "Current screen changed to: $currentScreen, User: $username")
    }

    when (currentScreen) {
        is ScreenState.Camera -> {
            CameraPreviewScreen(
                onPhotoTaken = { uri ->
                    if (!isNavigating) {
                        Log.d("CameraScreen", "Photo taken, navigating to preview: $uri")
                        viewModel.navigateToPreview(uri)
                    }
                },
                viewModel = viewModel,
                username = username,
                token = token,
                onOpenGallery = onOpenGallery
            )
        }
        is ScreenState.PhotoPreview -> {
            capturedImageUri?.let { uri ->
                PhotoPreviewScreen(
                    imageUri = uri,
                    token = token,
                    username = username,
                    onBackToCamera = {
                        if (!isNavigating) {
                            Log.d("CameraScreen", "Back to camera requested")
                            viewModel.navigateToCamera()
                        }
                    },
                    onSaveToGallery = {
                        // Photo is already saved in capturePhoto function
                        // This is just for UI feedback
                        Log.d("CameraScreen", "Save to gallery requested")
                    }
                )
            } ?: run {
                // If no image URI, automatically return to camera
                LaunchedEffect(Unit) {
                    Log.w("CameraScreen", "No image URI in preview, returning to camera")
                    viewModel.navigateToCamera()
                }
            }
        }
    }
}

/**
 * Screen state enum for navigation management.
 */
sealed class ScreenState {
    object Camera : ScreenState()
    object PhotoPreview : ScreenState()
}

/**
 * Camera preview screen with object detection and capture functionality.
 *
 * @param onPhotoTaken Callback when photo is captured
 * @param viewModel CameraViewModel for state management
 * @param username Current user's username
 */
@Composable
private fun CameraPreviewScreen(
    onPhotoTaken: (String) -> Unit,
    viewModel: CameraViewModel,
    username: String = "Guest",
    token: String = "",
    onOpenGallery: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Camera selector state - start with back camera
    var cameraSelector by remember { mutableStateOf(DEFAULT_BACK_CAMERA) }

    // Coordinate transformer for camera viewfinder
    val transformer = remember { MutableCoordinateTransformer() }
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var isCapturing by remember { mutableStateOf(false) }

    // Get detection state from ViewModel
    val detectedObjects by viewModel.detectedObjects
    val analyzerToBuffer by viewModel.analyzerToBuffer

    // Executor for image capture operations
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    // Ensure detector is activated when entering camera screen
    LaunchedEffect(Unit) {
        Log.d("CameraPreviewScreen", "Entering camera screen, activating detector")
        HighlightAnalysis.DetectorHolder.activate()
    }

    // Deactivate detector when component is disposed
    DisposableEffect(Unit) {
        onDispose {
            Log.d("CameraPreviewScreen", "Disposing camera preview, deactivating detector")
            HighlightAnalysis.DetectorHolder.deactivate()
        }
    }

    // Create object detection analyzer instance
    val objectDetectionAnalyzer = remember(context) {
        HighlightAnalysis.createObjectDetectionAnalyzer(
            context = context,
            executor = cameraExecutor,
            onDetectionResult = { objects, transformMatrix ->
                viewModel.updateDetectedObjects(objects, transformMatrix)
            }
        )
    }

    // Initialize camera when selector changes
    LaunchedEffect(cameraSelector) {
        try {
            val cameraProvider = ProcessCameraProvider.getInstance(context).get()

            // Build preview use case
            val preview = Preview.Builder()
                .build()
                .apply {
                    setSurfaceProvider { request ->
                        surfaceRequest = request
                    }
                }

            // Build image capture use case
            val imageCaptureUseCase = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            // Update imageCapture state
            imageCapture = imageCaptureUseCase

            // Unbind any existing use cases and bind new ones
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCaptureUseCase,
                objectDetectionAnalyzer
            )

            Log.d("CameraPreviewScreen", "Camera bound with all use cases")

        } catch (e: Exception) {
            Log.e("CameraPreviewScreen", "Camera initialization failed", e)
            Toast.makeText(context, "Camera initialization failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Camera viewfinder UI with capture controls
    Box(modifier = Modifier.fillMaxSize()) {
        // Camera viewfinder
        surfaceRequest?.let { request ->
            CameraXViewfinder(
                surfaceRequest = request,
                coordinateTransformer = transformer,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // Get transformation info from the camera
        val transformInfo by produceState<SurfaceRequest.TransformationInfo?>(
            null,
            surfaceRequest
        ) {
            surfaceRequest?.setTransformationInfoListener(Runnable::run) { transformationInfo ->
                value = transformationInfo
            }
            try {
                awaitCancellation()
            } finally {
                surfaceRequest?.clearTransformationInfoListener()
            }
        }

        // Canvas overlay for object detection bounding boxes
        Canvas(modifier = Modifier.fillMaxSize()) {
            transformInfo?.let { info ->
                val bufferToUiTransformMatrix = Matrix().apply {
                    setFrom(transformer.transformMatrix)
                    invert()
                }

                val analysisToSensor = android.graphics.Matrix()
                analyzerToBuffer.invert(analysisToSensor)

                val totalMatrix = analysisToSensor.apply {
                    postConcat(info.sensorToBufferTransform)
                }

                val totalMatrixCompose = Matrix().apply {
                    setFrom(totalMatrix)
                }

                detectedObjects.forEach { detectedObject ->
                    val bbox = detectedObject.bbox

                    val composeRect = androidx.compose.ui.geometry.Rect(
                        bbox.left,
                        bbox.top,
                        bbox.right,
                        bbox.bottom
                    )

                    val bufferRect = totalMatrixCompose.map(composeRect)
                    val uiRect = bufferToUiTransformMatrix.map(bufferRect)

                    drawRect(
                        color = Color.Red,
                        topLeft = Offset(uiRect.left, uiRect.top),
                        size = Size(uiRect.width, uiRect.height),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                    )

                    val labelOffset = Offset(uiRect.left, uiRect.top - 30.dp.toPx())
                    drawRect(
                        color = Color.Red,
                        topLeft = labelOffset,
                        size = Size(120.dp.toPx(), 25.dp.toPx())
                    )
                }
            }
        }

        // User info and object detection display
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "User: $username",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )

                // Show "My Photos" button only if user is logged in (not guest)
                if (token.isNotEmpty() && username != "Guest") {
                    Button(
                        onClick = onOpenGallery,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        ),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            text = "My Photos",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            Text(
                text = "Objects: ${detectedObjects.size}",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
            detectedObjects.take(3).forEach { obj ->
                Text(
                    text = "${obj.label}: ${(obj.score * 100).toInt()}%",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Camera switch button at top right
        FloatingActionButton(
            onClick = {
                cameraSelector = if (cameraSelector == DEFAULT_BACK_CAMERA) {
                    DEFAULT_FRONT_CAMERA
                } else {
                    DEFAULT_BACK_CAMERA
                }
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(56.dp),
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
        ) {
            Icon(
                Icons.Default.Cameraswitch,
                contentDescription = "Switch Camera",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        // Camera controls at the bottom
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            FloatingActionButton(
                onClick = {
                    if (!isCapturing) {
                        imageCapture?.let { capture ->
                            capturePhoto(
                                imageCapture = capture,
                                context = context,
                                executor = cameraExecutor,
                                onImageCaptured = { uri ->
                                    onPhotoTaken(uri)
                                    isCapturing = false
                                },
                                onError = { exception ->
                                    isCapturing = false
                                    Toast.makeText(context, "Capture failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                                }
                            )
                            isCapturing = true
                        }
                    }
                },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp)
            ) {
                if (isCapturing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White
                    )
                } else {
                    Icon(
                        Icons.Default.PhotoCamera,
                        contentDescription = "Take Photo",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

/**
 * Captures a photo and saves it to the device gallery.
 */
private fun capturePhoto(
    imageCapture: ImageCapture,
    context: Context,
    executor: Executor,
    onImageCaptured: (String) -> Unit,
    onError: (ImageCaptureException) -> Unit
) {
    val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.getDefault())
        .format(System.currentTimeMillis())

    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraApp")
        }
    }

    val outputOptions = ImageCapture.OutputFileOptions.Builder(
        context.contentResolver,
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        contentValues
    ).build()

    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onError(exception: ImageCaptureException) {
                Log.e("CameraScreen", "Photo capture failed: ${exception.message}", exception)
                onError(exception)
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val savedUri = output.savedUri ?: return
                Log.d("CameraScreen", "Photo saved successfully: $savedUri")
                onImageCaptured(savedUri.toString())
            }
        }
    )
}