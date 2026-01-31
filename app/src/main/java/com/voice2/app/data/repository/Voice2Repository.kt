package com.voice2.app.data.repository

import com.voice2.app.data.api.Voice2ApiService
import com.voice2.app.data.api.Transcription
import com.voice2.app.data.api.TodoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    suspend fun semanticSearch(query: String): Result<List<Transcription>> = runCatching { apiService.semanticSearch(query) }

    suspend fun getChat(id: UUID): Result<Transcription> = runCatching { apiService.getChat(id) }

    suspend fun uploadAudio(file: File, lat: Double? = null, lon: Double? = null): Result<Transcription> = withContext(Dispatchers.IO) {
        runCatching {
            val requestFile = file.asRequestBody("audio/mpeg".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
            val response = apiService.uploadAudio(body, lat, lon).execute()
            if (response.isSuccessful) {
                response.body() ?: throw Exception("Empty response")
            } else {
                throw Exception("Server error")
            }
        }
    }

    suspend fun transcribeText(text: String, lat: Double? = null, lon: Double? = null): Result<Transcription> = runCatching {
        apiService.transcribeText(mapOf("text" to text, "latitude" to lat, "longitude" to lon))
    }

    suspend fun enhanceChat(id: UUID): Result<Transcription> = runCatching { apiService.enhanceChat(id) }

    suspend fun rewriteChat(id: UUID, mode: String): Result<Transcription> = runCatching {
        apiService.rewriteChat(id, mapOf("mode" to mode))
    }

    suspend fun suggestTags(id: UUID): Result<List<String>> = runCatching {
        apiService.suggestTags(id)
    }

    suspend fun addTag(id: UUID, tagName: String): Result<Transcription> = runCatching {
        apiService.addTag(id, mapOf("tag_name" to tagName))
    }

    suspend fun createAlbum(id: UUID): Result<Map<String, String>> = runCatching {
        apiService.createAlbum(id)
    }

    suspend fun getTodos(): Result<List<TodoItem>> = runCatching { apiService.getTodos() }
    suspend fun createTodo(desc: String): Result<TodoItem> = runCatching { apiService.createTodo(mapOf("description" to desc)) }
    suspend fun updateTodo(id: UUID, comp: Boolean): Result<TodoItem> = runCatching { apiService.updateTodo(id, mapOf("completed" to comp)) }
}
