package com.example.notetaker.core.domain.repository

import com.example.notetaker.core.data.db.entity.NoteImageEntity
import com.example.notetaker.core.domain.model.NoteImage
import kotlinx.coroutines.flow.Flow

interface NoteImageRepository {
    fun observeNoteImages(noteId: String): Flow<List<NoteImage>>
    suspend fun getNoteImage(id: String): NoteImage?
    suspend fun saveNoteImage(image: NoteImageEntity)
    suspend fun saveNoteImages(images: List<NoteImageEntity>)
    suspend fun softDeleteNoteImage(id: String)
}
