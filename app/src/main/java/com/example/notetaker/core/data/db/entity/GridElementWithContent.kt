package com.example.notetaker.core.data.db.entity

import androidx.room.Embedded
import androidx.room.Relation

import com.example.notetaker.core.domain.model.GridElementWithContent

data class GridElementWithContent(
    @Embedded val element: GridElementEntity,

    @Relation(
        entity = NoteEntity::class,
        parentColumn = "noteId",
        entityColumn = "id"
    )
    val noteWithImages: NoteWithImages?
) {
    fun toDomain(): GridElementWithContent {
        return GridElementWithContent(
            element = element.toDomain(),
            note = noteWithImages?.note?.toDomain()?.copy(
                images = noteWithImages.images.map { it.toDomain() }
            )
        )
    }
}
