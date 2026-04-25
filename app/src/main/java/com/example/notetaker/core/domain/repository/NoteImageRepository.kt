package com.example.notetaker.core.domain.repository

import com.example.notetaker.core.data.db.entity.NoteImageEntity
import kotlinx.coroutines.flow.Flow

interface NoteImageRepository {
    fun observeNoteImages(noteId: String): Flow<List<NoteImageEntity>>
    suspend fun getNoteImage(id: String): NoteImageEntity?
    suspend fun saveNoteImage(image: NoteImageEntity)
    suspend fun saveNoteImages(images: List<NoteImageEntity>)
    suspend fun softDeleteNoteImage(id: String)
}
