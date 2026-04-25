package com.example.notetaker.core.data.db.entity

import androidx.room.Embedded
import androidx.room.Relation

data class NoteWithImages(
    @Embedded val note: NoteEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "noteId"
    )
    val images: List<NoteImageEntity>
)