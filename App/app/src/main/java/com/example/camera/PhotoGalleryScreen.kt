package com.example.camera

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.camera.login.ApiService
import com.example.camera.login.Photo
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Photo Gallery Screen that displays all user's photos from the server
 */
@Composable
fun PhotoGalleryScreen(
    token: String,
    username: String,
    onBackToCamera: () -> Unit
) {
    val context = LocalContext.current
    val apiService = remember { ApiService() }
    val scope = rememberCoroutineScope()

    var photos by remember { mutableStateOf<List<Photo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Load photos when screen opens
    LaunchedEffect(Unit) {
        isLoading = true
        errorMessage = null
        Log.d("PhotoGalleryScreen", "Loading photos for user: $username with token: ${token.take(10)}...")
        try {
            val loadedPhotos = apiService.fetchPhotos(token)
            Log.d("PhotoGalleryScreen", "Loaded ${loadedPhotos.size} photos")
            loadedPhotos.forEach { photo ->
                Log.d("PhotoGalleryScreen", "Photo: ${photo.filename}, timestamp: ${photo.timestamp}")
            }
            photos = loadedPhotos
        } catch (e: Exception) {
            Log.e("PhotoGalleryScreen", "Failed to load photos", e)
            errorMessage = "Failed to load photos: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    // Function to refresh photos
    val refreshPhotos: () -> Unit = {
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                photos = apiService.fetchPhotos(token)
            } catch (e: Exception) {
                errorMessage = "Failed to load photos: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 4.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBackToCamera) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Back to Camera"
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "$username's Photos",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "${photos.size} photos",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }

                    IconButton(onClick = refreshPhotos) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh"
                        )
                    }
                }
            }

            // Content area
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                when {
                    isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    errorMessage != null -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = errorMessage!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = refreshPhotos) {
                                Text("Retry")
                            }
                        }
                    }
                    photos.isEmpty() -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No photos yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Take some photos and upload them!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                    }
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(photos) { photo ->
                                PhotoGridItem(
                                    photo = photo,
                                    apiService = apiService,
                                    token = token
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Individual photo item in the grid
 */
@Composable
fun PhotoGridItem(
    photo: Photo,
    apiService: ApiService,
    token: String
) {
    var showDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable { showDialog = true },
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(apiService.getPhotoUrl(photo.filename))
                    .addHeader("Authorization", "Bearer $token")
                    .crossfade(true)
                    .build(),
                contentDescription = "Photo ${photo.filename}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Date overlay
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp),
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = formatDate(photo.timestamp),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White
                )
            }
        }
    }

    // Full screen dialog
    if (showDialog) {
        PhotoDetailDialog(
            photo = photo,
            apiService = apiService,
            token = token,
            onDismiss = { showDialog = false }
        )
    }
}

/**
 * Full screen dialog to view photo in detail
 */
@Composable
fun PhotoDetailDialog(
    photo: Photo,
    apiService: ApiService,
    token: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        title = {
            Text(text = photo.filename)
        },
        text = {
            Column {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(apiService.getPhotoUrl(photo.filename))
                        .addHeader("Authorization", "Bearer $token")
                        .crossfade(true)
                        .build(),
                    contentDescription = "Photo detail",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Uploaded: ${formatDateTime(photo.timestamp)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    )
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun formatDateTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}