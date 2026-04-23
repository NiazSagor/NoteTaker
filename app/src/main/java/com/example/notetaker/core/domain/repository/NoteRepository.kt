package com.example.notetaker.core.domain.repository

import com.example.notetaker.core.data.db.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

interface NoteRepository {
    fun observeNote(id: String): Flow<NoteEntity?>
    suspend fun getNote(id: String): NoteEntity?
    suspend fun saveNote(note: NoteEntity)
    suspend fun softDeleteNote(id: String)
}
