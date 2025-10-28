package com.codersee.routing

import com.codersee.model.PhotoListResponse
import com.codersee.repository.PhotoRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.io.path.div

fun Route.photoRoute(photoRepository: PhotoRepository) {

    // Get all photos metadata for the authenticated user
    get {
        val username = extractPrincipalUsername(call)

        if (username == null) {
            call.respond(HttpStatusCode.Unauthorized, "Not authenticated")
            return@get
        }

        println("Fetching photos for user: $username")
        val photos = photoRepository.getPhotosForUser(username)
        println("Found ${photos.size} photos for user: $username")
        photos.forEach { photo ->
            println("  - ${photo.filename} (${photo.timestamp})")
        }

        call.respond(HttpStatusCode.OK, PhotoListResponse(photos))
    }

    // Get a specific photo file
    get("/{filename}") {
        val username = extractPrincipalUsername(call)
        val filename = call.parameters["filename"]

        if (username == null) {
            call.respond(HttpStatusCode.Unauthorized, "Not authenticated")
            return@get
        }

        if (filename == null) {
            call.respond(HttpStatusCode.BadRequest, "Filename is required")
            return@get
        }

        println("Fetching photo file: $filename for user: $username")
        val file = (uploadDir / username / filename).toFile()

        if (file.exists() && file.isFile) {
            call.respondFile(file)
        } else {
            println("Photo file not found: ${file.absolutePath}")
            call.respond(HttpStatusCode.NotFound, "Photo not found")
        }
    }

    // Delete a specific photo
    delete("/{filename}") {
        val username = extractPrincipalUsername(call)
        val filename = call.parameters["filename"]

        if (username == null) {
            call.respond(HttpStatusCode.Unauthorized, "Not authenticated")
            return@delete
        }

        if (filename == null) {
            call.respond(HttpStatusCode.BadRequest, "Filename is required")
            return@delete
        }

        val deleted = photoRepository.deletePhoto(username, filename)

        if (deleted) {
            call.respond(HttpStatusCode.OK, "Photo deleted successfully")
        } else {
            call.respond(HttpStatusCode.NotFound, "Photo not found")
        }
    }
}