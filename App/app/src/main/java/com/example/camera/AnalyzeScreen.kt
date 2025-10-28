package com.example.camera

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Dialog that displays ML analysis results for a photo
 */
@Composable
fun AnalyzeDialog(
    imageUri: String,
    token: String,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isAnalyzing by remember { mutableStateOf(false) }
    var analysisResult by remember { mutableStateOf<AnalysisResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Start analysis when dialog opens
    LaunchedEffect(imageUri) {
        isAnalyzing = true
        errorMessage = null

        try {
            val result = analyzePhoto(context, imageUri, token)
            analysisResult = result
        } catch (e: Exception) {
            errorMessage = "Analysis failed: ${e.message}"
            Log.e("AnalyzeDialog", "Analysis error", e)
        } finally {
            isAnalyzing = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Photo Analysis",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Content
                when {
                    isAnalyzing -> {
                        // Loading state
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Analyzing photo...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    errorMessage != null -> {
                        // Error state
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "⚠️",
                                style = MaterialTheme.typography.displayMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = errorMessage ?: "Unknown error",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    analysisResult != null -> {
                        // Success state
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Caption section
                            Text(
                                text = "Description:",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Text(
                                    text = analysisResult!!.caption,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Model info
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Model:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = analysisResult!!.model,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Latency:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${analysisResult!!.latencyMs}ms",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }

                // Close button
                if (!isAnalyzing) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

/**
 * Result data class for analysis
 */
data class AnalysisResult(
    val caption: String,
    val model: String,
    val latencyMs: Int
)

/**
 * Calls the ML server to analyze a photo
 */
private suspend fun analyzePhoto(
    context: Context,
    imageUri: String,
    token: String
): AnalysisResult = withContext(Dispatchers.IO) {
    val baseUrl = "http://192.168.64.2:8080"
    val boundary = "----WebKitFormBoundary${System.currentTimeMillis()}"
    val url = URL("$baseUrl/api/analyze")

    Log.d("AnalyzeDialog", "Analyze URL: $url")

    val connection = url.openConnection() as HttpURLConnection
    connection.requestMethod = "POST"
    connection.setRequestProperty("Authorization", "Bearer $token")
    connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
    connection.doOutput = true
    connection.doInput = true
    connection.connectTimeout = 30000
    connection.readTimeout = 30000

    try {
        val outputStream = DataOutputStream(connection.outputStream)

        val lineEnd = "\r\n"
        val twoHyphens = "--"

        // Read image bytes from URI
        val imageBytes = context.contentResolver.openInputStream(Uri.parse(imageUri))?.use {
            it.readBytes()
        } ?: throw Exception("Cannot read image")

        // Add file part
        outputStream.writeBytes(twoHyphens + boundary + lineEnd)
        outputStream.writeBytes("Content-Disposition: form-data; name=\"image\"; filename=\"photo.jpg\"$lineEnd")
        outputStream.writeBytes("Content-Type: image/jpeg$lineEnd")
        outputStream.writeBytes(lineEnd)
        outputStream.write(imageBytes)
        outputStream.writeBytes(lineEnd)

        // End multipart
        outputStream.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd)
        outputStream.flush()
        outputStream.close()

        val responseCode = connection.responseCode
        Log.d("AnalyzeDialog", "Analyze response code: $responseCode")

        if (responseCode == HttpURLConnection.HTTP_OK) {
            val response = BufferedReader(InputStreamReader(connection.inputStream)).use {
                it.readText()
            }

            Log.d("AnalyzeDialog", "Analyze response: $response")

            val jsonResponse = JSONObject(response)
            AnalysisResult(
                caption = jsonResponse.getString("caption"),
                model = jsonResponse.getString("model"),
                latencyMs = jsonResponse.getInt("latency_ms")
            )
        } else {
            val errorResponse = connection.errorStream?.let {
                BufferedReader(InputStreamReader(it)).use { reader ->
                    reader.readText()
                }
            } ?: "Unknown error"
            Log.e("AnalyzeDialog", "Analyze error response: $errorResponse")
            throw Exception("Server returned error: $responseCode")
        }
    } finally {
        connection.disconnect()
    }
}