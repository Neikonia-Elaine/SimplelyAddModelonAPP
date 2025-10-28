package com.codersee.model

import kotlinx.serialization.Serializable

@Serializable
data class Photo(
    val filename: String,
    val username: String,
    val timestamp: Long,
    val description: String = ""
)

@Serializable
data class PhotoListResponse(
    val photos: List<Photo>
)