package com.example.camera

import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import com.example.camera.login.ApiService
import kotlinx.coroutines.launch

/**
 * Photo preview screen that displays captured images with polaroid-style effect.
 * Handles image rotation based on EXIF data and provides navigation controls.
 *
 * @param imageUri URI of the captured image
 * @param token Authentication token for uploads
 * @param username Current user's username
 * @param onBackToCamera Callback to return to camera screen
 * @param onSaveToGallery Callback to save image to gallery
 */
@Composable
fun PhotoPreviewScreen(
    imageUri: String,
    token: String = "",
    username: String = "Guest",
    isLoggedIn: Boolean = false,
    onBackToCamera: () -> Unit,
    onSaveToGallery: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apiService = remember { ApiService() }

    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var hasNavigated by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
    var uploadSuccess by remember { mutableStateOf<Boolean?>(null) }
    var showAnalyzeDialog by remember { mutableStateOf(false) }

    // Safe navigation function to prevent multiple calls
    val safeNavigateBack = remember {
        {
            if (!hasNavigated) {
                hasNavigated = true
                Log.d("PhotoPreviewScreen", "Safe navigate back called")
                onBackToCamera()
            } else {
                Log.d("PhotoPreviewScreen", "Navigation already triggered, ignoring")
            }
            Unit
        }
    }

    // Reset navigation flag when imageUri changes
    LaunchedEffect(imageUri) {
        hasNavigated = false
        Log.d("PhotoPreviewScreen", "Reset navigation flag for URI: $imageUri")
    }

    /**
     * Gets the correct rotation angle from EXIF orientation data.
     *
     * @param uri Image URI to read EXIF data from
     * @return Rotation angle in degrees (0, 90, 180, or 270)
     */
    fun getImageRotation(uri: Uri): Float {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (e: Exception) {
            Log.e("PhotoPreviewScreen", "Error reading EXIF data", e)
            0f
        }
    }

    /**
     * Rotates a bitmap by the specified degrees.
     *
     * @param bitmap Original bitmap
     * @param degrees Rotation angle in degrees
     * @return Rotated bitmap
     */
    fun rotateBitmap(bitmap: android.graphics.Bitmap, degrees: Float): android.graphics.Bitmap {
        return if (degrees == 0f) {
            bitmap
        } else {
            val matrix = Matrix()
            matrix.postRotate(degrees)
            android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
    }

    // Load bitmap from URI with correct orientation
    LaunchedEffect(imageUri) {
        try {
            val uri = Uri.parse(imageUri)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val originalBitmap = BitmapFactory.decodeStream(inputStream)
                originalBitmap?.let {
                    val rotation = getImageRotation(uri)
                    bitmap = rotateBitmap(it, rotation)
                    Log.d("PhotoPreviewScreen", "Bitmap loaded and rotated by $rotation degrees")
                }
            }
        } catch (e: Exception) {
            Log.e("PhotoPreviewScreen", "Error loading image", e)
        }
    }

    // Show analyze dialog
    if (showAnalyzeDialog && token.isNotEmpty() && username != "Guest") {
        AnalyzeDialog(
            imageUri = imageUri,
            token = token,
            onDismiss = { showAnalyzeDialog = false }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Photo display with polaroid-like effect
        Card(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(32.dp)
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(12.dp)
                ),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // The actual photo using Canvas
                bitmap?.let { bmp ->
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f / 4f)
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        val imageBitmap = bmp.asImageBitmap()

                        // Calculate scaling to fit the canvas while maintaining aspect ratio
                        val imageAspectRatio = imageBitmap.width.toFloat() / imageBitmap.height.toFloat()
                        val canvasAspectRatio = size.width / size.height

                        val (drawWidth, drawHeight) = if (imageAspectRatio > canvasAspectRatio) {
                            // Image is wider than canvas - fit to width
                            size.width to size.width / imageAspectRatio
                        } else {
                            // Image is taller than canvas - fit to height
                            size.height * imageAspectRatio to size.height
                        }

                        val offsetX: Int = (size.width - drawWidth).toInt() / 2
                        val offsetY: Int = (size.height - drawHeight).toInt() / 2

                        drawImage(
                            image = imageBitmap,
                            dstOffset = IntOffset(offsetX, offsetY),
                            dstSize = IntSize(drawWidth.toInt(), drawHeight.toInt())
                        )
                    }
                } ?: run {
                    // Loading placeholder
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f / 4f)
                            .background(Color.Gray.copy(alpha = 0.3f))
                            .clip(RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Polaroid-style bottom space
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // Back button
        FloatingActionButton(
            onClick = {
                if (!isUploading) {
                    safeNavigateBack()
                }
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.surface.copy(
                alpha = if (isUploading) 0.5f else 0.9f
            )
        ) {
            Icon(
                Icons.Default.ArrowBack,
                contentDescription = "Back to Camera",
                tint = MaterialTheme.colorScheme.onSurface.copy(
                    alpha = if (isUploading) 0.5f else 1f
                )
            )
        }

        // Action buttons at the bottom
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(32.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Continue taking photos button
            FloatingActionButton(
                onClick = {
                    if (!isUploading) {
                        safeNavigateBack()
                    }
                },
                containerColor = MaterialTheme.colorScheme.secondary.copy(
                    alpha = if (isUploading) 0.5f else 1f
                ),
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    Icons.Default.PhotoCamera,
                    contentDescription = "Continue Taking Photos",
                    tint = Color.White.copy(alpha = if (isUploading) 0.5f else 1f),
                    modifier = Modifier.size(24.dp)
                )
            }

            // Analyze button (only show if logged in)
            if (token.isNotEmpty() && username != "Guest") {
                FloatingActionButton(
                    onClick = {
                        if (!isUploading) {
                            showAnalyzeDialog = true
                        }
                    },
                    containerColor = Color(0xFF9C27B0).copy(
                        alpha = if (isUploading) 0.5f else 1f
                    ),
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        Icons.Default.Psychology,
                        contentDescription = "Analyze Photo",
                        tint = Color.White.copy(alpha = if (isUploading) 0.5f else 1f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Upload to server button (only show if logged in)
            if (token.isNotEmpty() && username != "Guest") {
                FloatingActionButton(
                    onClick = {
                        if (!isUploading) {
                            scope.launch {
                                isUploading = true
                                uploadSuccess = null

                                try {
                                    val uri = Uri.parse(imageUri)
                                    val filename = "photo_${System.currentTimeMillis()}.jpg"
                                    val success = apiService.uploadPhoto(context, uri, token, filename)

                                    uploadSuccess = success
                                    if (success) {
                                        Toast.makeText(context, "Photo uploaded successfully!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Upload failed", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    uploadSuccess = false
                                    Toast.makeText(context, "Upload error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    Log.e("PhotoPreviewScreen", "Upload error", e)
                                } finally {
                                    isUploading = false
                                }
                            }
                        }
                    },
                    containerColor = when(uploadSuccess) {
                        true -> Color.Green
                        false -> Color.Red
                        null -> MaterialTheme.colorScheme.tertiary
                    },
                    modifier = Modifier.size(64.dp)
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White
                        )
                    } else {
                        Icon(
                            Icons.Default.CloudUpload,
                            contentDescription = "Upload to Server",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Save to gallery button
            FloatingActionButton(
                onClick = {
                    if (!isUploading) {
                        onSaveToGallery()
                        Toast.makeText(context, "Photo saved to gallery!", Toast.LENGTH_SHORT).show()
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary.copy(
                    alpha = if (isUploading) 0.5f else 1f
                ),
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    Icons.Default.Save,
                    contentDescription = "Save to Gallery",
                    tint = Color.White.copy(alpha = if (isUploading) 0.5f else 1f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Action labels
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 32.dp, vertical = 8.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Text(
                text = "Continue",
                color = Color.White,
                style = MaterialTheme.typography.bodySmall
            )
            if (token.isNotEmpty() && username != "Guest") {
                Text(
                    text = "Analyze",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Upload",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                text = "Save Local",
                color = Color.White,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}