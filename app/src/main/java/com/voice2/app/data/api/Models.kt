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
    @Json(name = "todo_title") val todoTitle: String?,
    @Json(name = "original_text") val originalText: String? = null
)

@JsonClass(generateAdapter = true)
data class TranscribeTextRequest(
    val text: String,
    val latitude: Double? = null,
    val longitude: Double? = null
)

@JsonClass(generateAdapter = true)
data class AdvancedSearchResponse(
    val items: List<Transcription>,
    @Json(name = "tag_facets") val tagFacets: List<TagFacet> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TagFacet(
    val name: String,
    val id: UUID? = null,
    val color: String? = null,
    val count: Int? = null
)

@JsonClass(generateAdapter = true)
data class TodoItem(
    val id: UUID,
    val description: String,
    val completed: Boolean,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "completed_at") val completedAt: String?,
    @Json(name = "chat_id") val chatId: UUID?,
    @Json(name = "chat_title") val chatTitle: String? = null
)
