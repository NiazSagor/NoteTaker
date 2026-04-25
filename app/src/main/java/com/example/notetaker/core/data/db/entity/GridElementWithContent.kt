package com.example.notetaker.core.data.db.entity

import androidx.room.Embedded
import androidx.room.Relation

import com.example.notetaker.core.domain.model.GridElementWithContent

data class GridElementWithContent(
    @Embedded val element: GridElementEntity,
    @Relation(
        parentColumn = "noteId",
        entityColumn = "id"
    )
    val note: NoteEntity?
) {
    fun toDomain(): GridElementWithContent {
        return GridElementWithContent(
            element = element.toDomain(),
            note = note?.toDomain()
        )
    }
}
