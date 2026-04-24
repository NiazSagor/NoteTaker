package com.example.notetaker.core.data.db.entity

import androidx.room.Embedded
import androidx.room.Relation

data class GridElementWithContent(
    @Embedded val element: GridElementEntity,
    @Relation(
        parentColumn = "noteId",
        entityColumn = "id"
    )
    val note: NoteEntity?
)
