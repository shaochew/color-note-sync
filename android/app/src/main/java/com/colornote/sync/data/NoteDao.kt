package com.colornote.sync.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Transaction
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun getAllNotes(): Flow<List<NoteWithItems>>

    @Transaction
    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteById(id: String): NoteWithItems?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity)

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Delete
    suspend fun deleteNote(note: NoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: NoteItemEntity)

    @Update
    suspend fun updateItem(item: NoteItemEntity)

    @Delete
    suspend fun deleteItem(item: NoteItemEntity)

    @Query("SELECT * FROM note_items WHERE noteId = :noteId ORDER BY sortOrder ASC")
    suspend fun getItemsByNoteId(noteId: String): List<NoteItemEntity>

    @Query("DELETE FROM note_items WHERE noteId = :noteId")
    suspend fun deleteAllItemsByNoteId(noteId: String)
}
