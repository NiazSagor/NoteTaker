package com.example.notetaker.core.domain.repository

import com.example.notetaker.core.data.db.entity.NoteEntity
import com.example.notetaker.core.domain.model.Note
import kotlinx.coroutines.flow.Flow

interface NoteRepository {
    fun observeNote(id: String): Flow<Note?>
    suspend fun getNote(id: String): Note?
    suspend fun saveNote(note: NoteEntity)
    suspend fun softDeleteNote(id: String)
}
