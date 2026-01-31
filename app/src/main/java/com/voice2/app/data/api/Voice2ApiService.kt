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
    fun uploadAudio(
        @Part file: MultipartBody.Part,
        @Query("latitude") lat: Double? = null,
        @Query("longitude") lon: Double? = null
    ): retrofit2.Call<Transcription>

    @POST("chats/from_text/")
    suspend fun transcribeText(@Body body: Map<String, Any?>): Transcription

    @POST("ai/enhance/{chat_id}")
    suspend fun enhanceChat(@Path("chat_id") chatId: UUID): Transcription

    @POST("ai/rewrite/{chat_id}")
    suspend fun rewriteChat(@Path("chat_id") chatId: UUID, @Body body: Map<String, String>): Transcription

    @GET("ai/suggest-tags/{chat_id}")
    suspend fun suggestTags(@Path("chat_id") chatId: UUID): List<String>

    @POST("chats/{chat_id}/tags")
    suspend fun addTag(@Path("chat_id") chatId: UUID, @Body body: Map<String, String>): Transcription

    @POST("photos/albums/create/{chat_id}")
    suspend fun createAlbum(@Path("chat_id") chatId: UUID): Map<String, String>

    @GET("todos/")
    suspend fun getTodos(): List<TodoItem>

    @POST("todos/")
    suspend fun createTodo(@Body todo: Map<String, String>): TodoItem

    @PUT("todos/{id}")
    suspend fun updateTodo(@Path("id") id: UUID, @Body todo: Map<String, Boolean>): TodoItem
}
