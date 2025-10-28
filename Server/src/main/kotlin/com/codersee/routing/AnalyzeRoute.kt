package com.codersee.routing

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// ML server response format
@Serializable
data class MLAnalysisResponse(
    val model: String,
    val caption: String,
    val latency_ms: Int
)

// HTTP client for calling ML server
private val analyzeHttpClient = HttpClient()

// ML server configuration
private val ML_BASE_URL = System.getenv("ML_BASE_URL") ?: "http://ml:8001"
private val ML_ANALYZE_URL = "$ML_BASE_URL/analyze-image"
private val ML_ANALYZE_TOKEN = System.getenv("SERVICE_TOKEN") ?: "dev-token"

fun Route.analyzeRoute() {
    post {
        val multipartData = call.receiveMultipart()
        var imageBytes: ByteArray? = null

        // Extract image from multipart
        multipartData.forEachPart { part ->
            when (part) {
                is PartData.FileItem -> {
                    imageBytes = part.streamProvider().readBytes()
                }
                else -> {}
            }
            part.dispose()
        }

        if (imageBytes == null) {
            call.respond(HttpStatusCode.BadRequest, "No image provided")
            return@post
        }

        // Call ML server
        try {
            println("Calling ML server for analysis at: $ML_ANALYZE_URL")
            val response: HttpResponse = analyzeHttpClient.post(ML_ANALYZE_URL) {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $ML_ANALYZE_TOKEN")
                }
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("image", imageBytes!!, Headers.build {
                                append(HttpHeaders.ContentType, "image/jpeg")
                                append(HttpHeaders.ContentDisposition, "filename=\"analyze.jpg\"")
                            })
                        }
                    )
                )
            }

            if (response.status == HttpStatusCode.OK) {
                val mlResponse = Json.decodeFromString<MLAnalysisResponse>(response.bodyAsText())
                println("ML Analysis result: ${mlResponse.caption}")

                // Return ML response directly to client
                call.respond(HttpStatusCode.OK, mlResponse)
            } else {
                println("ML server returned error: ${response.status}")
                call.respond(HttpStatusCode.InternalServerError, "ML server error")
            }
        } catch (e: Exception) {
            println("Error calling ML server for analysis: ${e.message}")
            e.printStackTrace()
            call.respond(HttpStatusCode.InternalServerError, "Analysis failed: ${e.message}")
        }
    }
}