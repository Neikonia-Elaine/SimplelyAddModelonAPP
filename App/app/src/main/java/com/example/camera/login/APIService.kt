package com.example.camera.login

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class Photo(
    val filename: String,
    val username: String,
    val timestamp: Long,
    val description: String = ""
)

class ApiService {
    private val baseUrl = "http://192.168.64.2:8080"

    suspend fun register(username: String, password: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("$baseUrl/api/user")
                Log.d("ApiService", "Register URL: $url")

                val connection = url.openConnection() as HttpURLConnection

                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val json = JSONObject()
                json.put("username", username)
                json.put("password", password)

                Log.d("ApiService", "Sending register request for: $username")

                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(json.toString())
                    writer.flush()
                }

                val responseCode = connection.responseCode
                Log.d("ApiService", "Register response code: $responseCode")

                if (responseCode != HttpURLConnection.HTTP_CREATED) {
                    try {
                        val errorStream = connection.errorStream
                        if (errorStream != null) {
                            val errorResponse = BufferedReader(InputStreamReader(errorStream)).use { it.readText() }
                            Log.e("ApiService", "Register error response: $errorResponse")
                        }
                    } catch (e: Exception) {
                        Log.e("ApiService", "Error reading error stream", e)
                    }
                }

                connection.disconnect()
                responseCode == HttpURLConnection.HTTP_CREATED
            } catch (e: Exception) {
                Log.e("ApiService", "Register error: ${e.message}", e)
                e.printStackTrace()
                false
            }
        }
    }

    suspend fun login(username: String, password: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("$baseUrl/api/auth")
                Log.d("ApiService", "Login URL: $url")

                val connection = url.openConnection() as HttpURLConnection

                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val json = JSONObject()
                json.put("username", username)
                json.put("password", password)

                Log.d("ApiService", "Sending login request for: $username")

                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(json.toString())
                    writer.flush()
                }

                val responseCode = connection.responseCode
                Log.d("ApiService", "Login response code: $responseCode")

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                        reader.readText()
                    }

                    Log.d("ApiService", "Login response: $response")

                    val jsonResponse = JSONObject(response)
                    val token = jsonResponse.getString("token")

                    connection.disconnect()
                    token
                } else {
                    try {
                        val errorStream = connection.errorStream
                        if (errorStream != null) {
                            val errorResponse = BufferedReader(InputStreamReader(errorStream)).use { it.readText() }
                            Log.e("ApiService", "Login error response: $errorResponse")
                        }
                    } catch (e: Exception) {
                        Log.e("ApiService", "Error reading error stream", e)
                    }

                    connection.disconnect()
                    null
                }
            } catch (e: Exception) {
                Log.e("ApiService", "Login error: ${e.message}", e)
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * Upload a photo to the server
     */
    suspend fun uploadPhoto(
        context: Context,
        imageUri: Uri,
        token: String,
        filename: String
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val boundary = "----WebKitFormBoundary${System.currentTimeMillis()}"
                val url = URL("$baseUrl/api/upload/$filename")

                Log.d("ApiService", "Upload URL: $url")

                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                connection.doOutput = true
                connection.doInput = true
                connection.connectTimeout = 30000
                connection.readTimeout = 30000

                val outputStream = DataOutputStream(connection.outputStream)

                // Start multipart
                val lineEnd = "\r\n"
                val twoHyphens = "--"

                // Add file part - this matches what the server expects
                outputStream.writeBytes(twoHyphens + boundary + lineEnd)
                outputStream.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"$filename\"$lineEnd")
                outputStream.writeBytes("Content-Type: image/jpeg$lineEnd")
                outputStream.writeBytes(lineEnd)

                // Read and write image data
                context.contentResolver.openInputStream(imageUri)?.use { inputStream ->
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    var totalBytes = 0
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                    }
                    Log.d("ApiService", "Uploaded $totalBytes bytes")
                }

                // End multipart
                outputStream.writeBytes(lineEnd)
                outputStream.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd)

                outputStream.flush()
                outputStream.close()

                val responseCode = connection.responseCode
                Log.d("ApiService", "Upload response code: $responseCode")

                if (responseCode == HttpURLConnection.HTTP_CREATED) {
                    Log.d("ApiService", "Upload successful!")
                    connection.disconnect()
                    true
                } else {
                    try {
                        val errorStream = connection.errorStream
                        if (errorStream != null) {
                            val errorResponse = BufferedReader(InputStreamReader(errorStream)).use { it.readText() }
                            Log.e("ApiService", "Upload error response: $errorResponse")
                        }
                    } catch (e: Exception) {
                        Log.e("ApiService", "Error reading error stream", e)
                    }
                    connection.disconnect()
                    false
                }
            } catch (e: Exception) {
                Log.e("ApiService", "Upload error: ${e.message}", e)
                e.printStackTrace()
                false
            }
        }
    }

    /**
     * Fetch all photos for the authenticated user
     */
    suspend fun fetchPhotos(token: String): List<Photo> {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("$baseUrl/api/photos")
                Log.d("ApiService", "Fetch photos URL: $url")

                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val responseCode = connection.responseCode
                Log.d("ApiService", "Fetch photos response code: $responseCode")

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
                    Log.d("ApiService", "Fetch photos response: $response")

                    val jsonResponse = JSONObject(response)
                    val photosArray = jsonResponse.getJSONArray("photos")

                    val photos = mutableListOf<Photo>()
                    for (i in 0 until photosArray.length()) {
                        val photoJson = photosArray.getJSONObject(i)
                        photos.add(
                            Photo(
                                filename = photoJson.getString("filename"),
                                username = photoJson.getString("username"),
                                timestamp = photoJson.getLong("timestamp"),
                                description = photoJson.optString("description", "")
                            )
                        )
                    }

                    connection.disconnect()
                    photos
                } else {
                    connection.disconnect()
                    emptyList()
                }
            } catch (e: Exception) {
                Log.e("ApiService", "Fetch photos error: ${e.message}", e)
                e.printStackTrace()
                emptyList()
            }
        }
    }

    /**
     * Get the URL for a specific photo
     */
    fun getPhotoUrl(filename: String): String {
        return "$baseUrl/api/photos/$filename"
    }
}