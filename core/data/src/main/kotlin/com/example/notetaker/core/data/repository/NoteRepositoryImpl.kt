package com.example.notetaker.core.data.repository

import com.example.notetaker.core.data.db.dao.NoteDao
import com.example.notetaker.core.data.db.entity.NoteEntity
import com.example.notetaker.core.domain.di.IoDispatcher
import com.example.notetaker.core.domain.repository.NoteRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepositoryImpl @Inject constructor(
    private val noteDao: NoteDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : NoteRepository {
    override fun observeNote(id: String): Flow<NoteEntity?> {
        return noteDao.observeNote(id)
    }

    override suspend fun getNote(id: String): NoteEntity? = withContext(ioDispatcher) {
        noteDao.getNote(id)
    }

    override suspend fun saveNote(note: NoteEntity) = withContext(ioDispatcher) {
        noteDao.upsert(note)
    }

    override suspend fun softDeleteNote(id: String) = withContext(ioDispatcher) {
        noteDao.softDelete(id)
    }
}
