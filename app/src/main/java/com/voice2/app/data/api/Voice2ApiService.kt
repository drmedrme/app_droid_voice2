package com.voice2.app.data.api

import okhttp3.MultipartBody
import retrofit2.http.*
import java.util.UUID

interface Voice2ApiService {
    @GET("chats/")
    suspend fun getChats(): List<Transcription>

    @GET("chats/search/")
    suspend fun searchChats(@Query("query") query: String): List<Transcription>

    @GET("chats/{chat_id}")
    suspend fun getChat(@Path("chat_id") chatId: UUID): Transcription

    @POST("audio/transcribe/")
    @Multipart
    suspend fun uploadAudio(
        @Part file: MultipartBody.Part,
        @Query("latitude") lat: Double? = null,
        @Query("longitude") lon: Double? = null
    ): Transcription

    @POST("chats/from_text/")
    suspend fun transcribeText(@Body body: TranscribeTextRequest): Transcription

    @POST("ai/enhance/{chat_id}")
    suspend fun enhanceChat(@Path("chat_id") chatId: UUID): Transcription

    @POST("ai/rewrite/{chat_id}")
    suspend fun rewriteChat(@Path("chat_id") chatId: UUID, @Body body: Map<String, String>): Transcription

    @GET("ai/suggest-tags/{chat_id}")
    suspend fun suggestTags(@Path("chat_id") chatId: UUID): List<String>

    @POST("tags/")
    suspend fun createTag(@Body body: Map<String, String>): Tag

    @GET("tags/")
    suspend fun getTags(): List<Tag>

    @POST("chats/{chat_id}/tags")
    suspend fun updateChatTags(@Path("chat_id") chatId: UUID, @Body tagIds: List<UUID>): Transcription

    @POST("ai/revert/{chat_id}")
    suspend fun revertChat(@Path("chat_id") chatId: UUID): Transcription

    @GET("chats/{chat_id}/related")
    suspend fun getRelatedChats(@Path("chat_id") chatId: UUID): List<Transcription>

    @DELETE("chats/{chat_id}")
    suspend fun deleteChat(@Path("chat_id") chatId: UUID)

    @POST("todos/extract/{chat_id}")
    suspend fun extractTodos(@Path("chat_id") chatId: UUID): List<TodoItem>

    @POST("photos/albums/create/{chat_id}")
    suspend fun createAlbum(@Path("chat_id") chatId: UUID): Map<String, String?>

    @GET("todos/")
    suspend fun getTodos(): List<TodoItem>

    @POST("todos/")
    suspend fun createTodo(@Body todo: Map<String, String>): TodoItem

    @PUT("todos/{id}")
    suspend fun updateTodo(@Path("id") id: UUID, @Body todo: Map<String, Boolean>): TodoItem

    @POST("todos/{id}/toggle")
    suspend fun toggleTodo(@Path("id") id: UUID): TodoItem

    @DELETE("todos/completed")
    suspend fun deleteCompletedTodos(): Map<String, Int>

    @DELETE("todos/{id}")
    suspend fun deleteTodo(@Path("id") id: UUID): TodoItem

    @PATCH("todos/{id}")
    suspend fun patchTodo(@Path("id") id: UUID, @Body body: Map<String, @JvmSuppressWildcards Any>): TodoItem
}
