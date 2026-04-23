package com.example.notetaker.core.data.repository

import com.example.notetaker.core.data.db.dao.NoteDao
import com.example.notetaker.core.data.db.entity.NoteEntity
import com.example.notetaker.core.domain.repository.NoteRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepositoryImpl @Inject constructor(
    private val noteDao: NoteDao
) : NoteRepository {
    override fun observeNote(id: String): Flow<NoteEntity?> {
        return noteDao.observeNote(id)
    }

    override suspend fun getNote(id: String): NoteEntity? {
        return noteDao.getNote(id)
    }

    override suspend fun saveNote(note: NoteEntity) {
        noteDao.upsert(note)
    }

    override suspend fun softDeleteNote(id: String) {
        noteDao.softDelete(id)
    }
}
