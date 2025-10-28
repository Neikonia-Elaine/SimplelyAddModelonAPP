package com.codersee.routing

import com.codersee.repository.PhotoRepository
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.path.Path
import kotlin.io.path.div
import java.nio.file.Files.createDirectories

val uploadDir = Path("uploads/")

// ML server response format
@Serializable
data class MLResponse(
    val model: String,
    val caption: String,
    val latency_ms: Int
)

// HTTP client for calling ML server
private val httpClient = HttpClient(CIO)

// ML server configuration - read from environment variables
private val ML_SERVER_URL = System.getenv("ML_URL") ?: "http://ml:8001/analyze-image"
private val ML_SERVICE_TOKEN = System.getenv("SERVICE_TOKEN") ?: "dev-token"

fun Route.uploadRoute(photoRepository: PhotoRepository) {
    get("/{filename}") {
        val username = extractPrincipalUsername(call)!!
        val filename = call.parameters["filename"]!!
        call.respondFile((uploadDir / username / filename).toFile())
    }

    post("/{filename}") {
        val multipartData = call.receiveMultipart(formFieldLimit = 1024 * 1024 * 100)
        var fileDescription = ""

        val username = extractPrincipalUsername(call)!!
        val filename = call.parameters["filename"]!!

        // Ensure directory exists
        createDirectories(uploadDir / username)
        val file: java.io.File = (uploadDir / username / filename).toFile()

        // Save uploaded file
        multipartData.forEachPart { part ->
            when (part) {
                is PartData.FormItem -> {
                    fileDescription = part.value
                }

                is PartData.FileItem -> {
                    part.provider().copyAndClose(file.writeChannel())
                }

                else -> {}
            }
            part.dispose()
        }

        // Call ML server to get image description
        val mlDescription = try {
            println("Calling ML server at: $ML_SERVER_URL")
            val response: HttpResponse = httpClient.post(ML_SERVER_URL) {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $ML_SERVICE_TOKEN")
                }
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("image", file.readBytes(), Headers.build {
                                append(HttpHeaders.ContentType, "image/jpeg")
                                append(HttpHeaders.ContentDisposition, "filename=\"$filename\"")
                            })
                        }
                    )
                )
            }

            if (response.status == HttpStatusCode.OK) {
                val mlResponse = Json.decodeFromString<MLResponse>(response.bodyAsText())
                println("ML Model caption: ${mlResponse.caption}")
                mlResponse.caption
            } else {
                println("ML server returned error: ${response.status}")
                ""  // Use empty description if ML service fails
            }
        } catch (e: Exception) {
            println("Error calling ML server: ${e.message}")
            e.printStackTrace()
            ""  // Use empty description if call fails
        }

        // Save photo metadata with ML-generated description
        val timestamp = System.currentTimeMillis()
        photoRepository.addPhoto(
            username = username,
            filename = filename,
            timestamp = timestamp,
            description = mlDescription
        )

        call.respond(HttpStatusCode.Created)
    }
}