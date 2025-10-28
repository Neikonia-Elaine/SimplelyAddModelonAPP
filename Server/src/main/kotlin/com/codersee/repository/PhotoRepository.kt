package com.codersee.repository

import com.codersee.model.Photo
import java.io.File
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries

class PhotoRepository(private val uploadDir: Path) {

    // In-memory storage for photo metadata (description, timestamp)
    private val photoMetadata = mutableMapOf<String, MutableMap<String, PhotoMetadata>>()

    data class PhotoMetadata(
        val timestamp: Long,
        val description: String
    )

    /**
     * Add a photo with metadata
     */
    fun addPhoto(username: String, filename: String, timestamp: Long, description: String) {
        if (!photoMetadata.containsKey(username)) {
            photoMetadata[username] = mutableMapOf()
        }
        photoMetadata[username]?.put(filename, PhotoMetadata(timestamp, description))
        println("Added photo metadata: $filename for user: $username with description: $description")
    }

    /**
     * Get all photos for a specific user
     */
    fun getPhotosForUser(username: String): List<Photo> {
        val userDir = uploadDir / username

        println("Looking for photos in directory: ${userDir.toAbsolutePath()}")

        if (!userDir.exists()) {
            println("Directory does not exist: ${userDir.toAbsolutePath()}")
            return emptyList()
        }

        val files = userDir.listDirectoryEntries()
        println("Found ${files.size} total files in directory")

        val photos = files
            .filter { it.toFile().isFile }
            .filter {
                val name = it.fileName.toString().lowercase()
                val isImage = name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")
                println("File: ${it.fileName}, isImage: $isImage")
                isImage
            }
            .map { filePath ->
                val file = filePath.toFile()
                val metadata = photoMetadata[username]?.get(file.name)

                println("Adding photo: ${file.name}, size: ${file.length()} bytes")
                Photo(
                    filename = file.name,
                    username = username,
                    timestamp = metadata?.timestamp ?: file.lastModified(),
                    description = metadata?.description ?: ""
                )
            }
            .sortedByDescending { it.timestamp }

        println("Returning ${photos.size} photos")
        return photos
    }

    /**
     * Check if a photo exists for a user
     */
    fun photoExists(username: String, filename: String): Boolean {
        val file = (uploadDir / username / filename).toFile()
        return file.exists() && file.isFile
    }

    /**
     * Delete a photo
     */
    fun deletePhoto(username: String, filename: String): Boolean {
        val file = (uploadDir / username / filename).toFile()
        val deleted = if (file.exists() && file.isFile) {
            file.delete()
        } else {
            false
        }

        // Also remove from metadata if deleted successfully
        if (deleted) {
            photoMetadata[username]?.remove(filename)
        }

        return deleted
    }
}