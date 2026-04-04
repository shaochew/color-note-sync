package com.colornote.sync.data

import androidx.room.Embedded
import androidx.room.Relation

data class NoteWithItems(
    @Embedded val note: NoteEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "noteId"
    )
    val items: List<NoteItemEntity>
)
