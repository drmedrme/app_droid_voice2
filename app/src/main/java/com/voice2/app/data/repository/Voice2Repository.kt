package com.voice2.app.data.repository

import com.voice2.app.data.api.Voice2ApiService
import com.voice2.app.data.api.Tag
import com.voice2.app.data.api.Transcription
import com.voice2.app.data.api.TodoItem
import com.voice2.app.data.api.TranscribeTextRequest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Voice2Repository @Inject constructor(
    private val apiService: Voice2ApiService
) {
    suspend fun getChats(): Result<List<Transcription>> = runCatching { apiService.getChats() }
    
    suspend fun searchChats(query: String): Result<List<Transcription>> = runCatching { apiService.searchChats(query) }

    suspend fun getChat(id: UUID): Result<Transcription> = runCatching { apiService.getChat(id) }

    suspend fun uploadAudio(file: File, lat: Double? = null, lon: Double? = null): Result<Transcription> = runCatching {
        val requestFile = file.asRequestBody("audio/mpeg".toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
        apiService.uploadAudio(body, lat, lon)
    }

    suspend fun transcribeText(text: String, lat: Double? = null, lon: Double? = null): Result<Transcription> = runCatching {
        apiService.transcribeText(TranscribeTextRequest(text = text, latitude = lat, longitude = lon))
    }

    suspend fun enhanceChat(id: UUID): Result<Transcription> = runCatching { apiService.enhanceChat(id) }

    suspend fun rewriteChat(id: UUID, mode: String): Result<Transcription> = runCatching {
        apiService.rewriteChat(id, mapOf("mode" to mode))
    }

    suspend fun suggestTags(id: UUID): Result<List<String>> = runCatching {
        apiService.suggestTags(id)
    }

    suspend fun getTags(): Result<List<Tag>> = runCatching { apiService.getTags() }

    suspend fun createTag(name: String): Result<Tag> = runCatching {
        apiService.createTag(mapOf("name" to name))
    }

    suspend fun updateChatTags(chatId: UUID, tagIds: List<UUID>): Result<Transcription> = runCatching {
        apiService.updateChatTags(chatId, tagIds)
    }

    suspend fun addTag(chatId: UUID, tagName: String): Result<Transcription> = runCatching {
        // Two-step: create tag first, then associate by ID
        val tag = apiService.createTag(mapOf("name" to tagName))
        val chat = apiService.getChat(chatId)
        val currentTagIds = chat.tags.map { it.id }
        apiService.updateChatTags(chatId, currentTagIds + tag.id)
    }

    suspend fun revertChat(id: UUID): Result<Transcription> = runCatching { apiService.revertChat(id) }

    suspend fun getRelatedChats(id: UUID): Result<List<Transcription>> = runCatching { apiService.getRelatedChats(id) }

    suspend fun deleteChat(id: UUID): Result<Unit> = runCatching { apiService.deleteChat(id) }

    suspend fun extractTodos(chatId: UUID): Result<List<TodoItem>> = runCatching { apiService.extractTodos(chatId) }

    suspend fun createAlbum(id: UUID): Result<Map<String, String?>> = runCatching {
        apiService.createAlbum(id)
    }

    suspend fun getTodos(): Result<List<TodoItem>> = runCatching { apiService.getTodos() }
    suspend fun createTodo(desc: String): Result<TodoItem> = runCatching { apiService.createTodo(mapOf("description" to desc)) }
    suspend fun updateTodo(id: UUID, comp: Boolean): Result<TodoItem> = runCatching { apiService.updateTodo(id, mapOf("completed" to comp)) }
    suspend fun toggleTodo(id: UUID): Result<TodoItem> = runCatching { apiService.toggleTodo(id) }
    suspend fun deleteTodo(id: UUID): Result<TodoItem> = runCatching { apiService.deleteTodo(id) }
    suspend fun deleteCompletedTodos(): Result<Int> = runCatching { apiService.deleteCompletedTodos()["deleted_count"] ?: 0 }
    suspend fun updateTodoDescription(id: UUID, description: String): Result<TodoItem> = runCatching {
        apiService.patchTodo(id, mapOf("description" to description))
    }
}
