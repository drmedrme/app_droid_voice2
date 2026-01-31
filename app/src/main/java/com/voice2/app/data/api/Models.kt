package com.voice2.app.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.UUID

@JsonClass(generateAdapter = true)
data class Tag(
    val id: UUID,
    val name: String,
    val color: String?
)

@JsonClass(generateAdapter = true)
data class Transcription(
    val id: UUID,
    val text: String,
    val timestamp: String,
    @Json(name = "photo_album_url") val photoAlbumUrl: String?,
    @Json(name = "audio_filename") val audioFilename: String?,
    val tags: List<Tag> = emptyList(),
    @Json(name = "merged_title") val mergedTitle: String?,
    @Json(name = "todo_title") val todoTitle: String?
)

@JsonClass(generateAdapter = true)
data class TodoItem(
    val id: UUID,
    val description: String,
    val completed: Boolean,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "completed_at") val completedAt: String?,
    @Json(name = "chat_id") val chatId: UUID?
)
