package com.colornote.sync.api

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

// ---------------------------------------------------------------------------
// Data classes matching backend JSON format
// ---------------------------------------------------------------------------

data class NoteItemResponse(
    val id: String,
    @SerializedName("note_id") val noteId: String,
    val text: String,
    @SerializedName("is_done") val isDone: Boolean,
    @SerializedName("sort_order") val sortOrder: Int,
    @SerializedName("created_at") val createdAt: String
)

data class NoteResponse(
    val id: String,
    val title: String,
    val color: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("created_at") val createdAt: String,
    val items: List<NoteItemResponse>
)

data class CreateNoteRequest(
    val title: String = "",
    val color: String = "#FFEB3B"
)

data class UpdateNoteRequest(
    val title: String? = null,
    val color: String? = null
)

data class CreateItemRequest(
    val text: String = "",
    @SerializedName("sort_order") val sortOrder: Int = 0
)

data class UpdateItemRequest(
    val text: String? = null,
    @SerializedName("is_done") val isDone: Boolean? = null,
    @SerializedName("sort_order") val sortOrder: Int? = null
)

data class ReorderEntry(
    val id: String,
    @SerializedName("sort_order") val sortOrder: Int
)

data class SyncRequest(
    val notes: List<NoteResponse>
)

data class SyncResponse(
    val notes: List<NoteResponse>
)

data class ExportResponse(
    val notes: List<NoteResponse>
)

data class ImportRequest(
    val notes: List<NoteResponse>
)

data class ImportResponse(
    val message: String,
    val count: Int
)

data class MessageResponse(
    val message: String
)

// ---------------------------------------------------------------------------
// Retrofit API interface
// ---------------------------------------------------------------------------

interface ApiService {

    // Notes
    @GET("api/notes")
    suspend fun getNotes(): Response<List<NoteResponse>>

    @GET("api/notes/{id}")
    suspend fun getNote(@Path("id") id: String): Response<NoteResponse>

    @POST("api/notes")
    suspend fun createNote(@Body body: CreateNoteRequest): Response<NoteResponse>

    @PUT("api/notes/{id}")
    suspend fun updateNote(
        @Path("id") id: String,
        @Body body: UpdateNoteRequest
    ): Response<NoteResponse>

    @DELETE("api/notes/{id}")
    suspend fun deleteNote(@Path("id") id: String): Response<MessageResponse>

    // Note Items
    @POST("api/notes/{noteId}/items")
    suspend fun createItem(
        @Path("noteId") noteId: String,
        @Body body: CreateItemRequest
    ): Response<NoteItemResponse>

    @PUT("api/notes/{noteId}/items/{itemId}")
    suspend fun updateItem(
        @Path("noteId") noteId: String,
        @Path("itemId") itemId: String,
        @Body body: UpdateItemRequest
    ): Response<NoteItemResponse>

    @DELETE("api/notes/{noteId}/items/{itemId}")
    suspend fun deleteItem(
        @Path("noteId") noteId: String,
        @Path("itemId") itemId: String
    ): Response<MessageResponse>

    @PUT("api/notes/{noteId}/items/reorder")
    suspend fun reorderItems(
        @Path("noteId") noteId: String,
        @Body body: List<ReorderEntry>
    ): Response<NoteResponse>

    // Sync
    @POST("api/sync")
    suspend fun sync(@Body body: SyncRequest): Response<SyncResponse>

    // Export / Import
    @GET("api/export")
    suspend fun exportData(): Response<ExportResponse>

    @POST("api/import")
    suspend fun importData(@Body body: ImportRequest): Response<ImportResponse>
}
