package com.colornote.sync

import com.colornote.sync.api.ApiService
import com.colornote.sync.api.NoteItemResponse
import com.colornote.sync.api.NoteResponse
import com.colornote.sync.api.SyncRequest
import com.colornote.sync.data.NoteDao
import com.colornote.sync.data.NoteEntity
import com.colornote.sync.data.NoteItemEntity
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class SyncResult(
    val success: Boolean,
    val noteCount: Int = 0,
    val message: String = ""
)

class SyncManager(
    private val noteDao: NoteDao,
    private val apiService: ApiService
) {

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    suspend fun sync(): SyncResult {
        return try {
            // 1. Get all local notes with items
            val localNotes = getLocalNotesSnapshot()

            // 2. Convert to API format
            val apiNotes = localNotes.map { noteWithItems ->
                NoteResponse(
                    id = noteWithItems.note.id,
                    title = noteWithItems.note.title,
                    color = noteWithItems.note.color,
                    updatedAt = millisToIso(noteWithItems.note.updatedAt),
                    createdAt = millisToIso(noteWithItems.note.createdAt),
                    items = noteWithItems.items.map { item ->
                        NoteItemResponse(
                            id = item.id,
                            noteId = item.noteId,
                            text = item.text,
                            isDone = item.isDone,
                            sortOrder = item.sortOrder,
                            createdAt = millisToIso(item.createdAt)
                        )
                    }
                )
            }

            // 3. POST to /api/sync
            val response = apiService.sync(SyncRequest(notes = apiNotes))

            if (!response.isSuccessful) {
                return SyncResult(
                    success = false,
                    message = "Server error: ${response.code()} ${response.message()}"
                )
            }

            val syncResponse = response.body()
                ?: return SyncResult(success = false, message = "Empty response from server")

            // 4. Upsert server state into local Room DB
            for (serverNote in syncResponse.notes) {
                val noteEntity = NoteEntity(
                    id = serverNote.id,
                    title = serverNote.title,
                    color = serverNote.color,
                    updatedAt = isoToMillis(serverNote.updatedAt),
                    createdAt = isoToMillis(serverNote.createdAt)
                )
                noteDao.insertNote(noteEntity)

                // Delete old items and insert new ones
                noteDao.deleteAllItemsByNoteId(serverNote.id)
                for (serverItem in serverNote.items) {
                    val itemEntity = NoteItemEntity(
                        id = serverItem.id,
                        noteId = serverItem.noteId,
                        text = serverItem.text,
                        isDone = serverItem.isDone,
                        sortOrder = serverItem.sortOrder,
                        createdAt = isoToMillis(serverItem.createdAt)
                    )
                    noteDao.insertItem(itemEntity)
                }
            }

            SyncResult(
                success = true,
                noteCount = syncResponse.notes.size,
                message = "Synced ${syncResponse.notes.size} notes"
            )
        } catch (e: Exception) {
            SyncResult(
                success = false,
                message = "Sync failed: ${e.localizedMessage ?: "Unknown error"}"
            )
        }
    }

    /**
     * Collect the Flow once to get a snapshot of all notes with items.
     */
    private suspend fun getLocalNotesSnapshot(): List<com.colornote.sync.data.NoteWithItems> {
        return noteDao.getAllNotes().first()
    }

    private fun millisToIso(millis: Long): String {
        return isoFormat.format(Date(millis))
    }

    private fun isoToMillis(iso: String): Long {
        return try {
            isoFormat.parse(iso)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
}
