package com.example.notetaker.core.data.repository

import com.example.notetaker.core.data.db.dao.NoteImageDao
import com.example.notetaker.core.data.db.entity.NoteImageEntity
import com.example.notetaker.core.domain.di.IoDispatcher
import com.example.notetaker.core.domain.repository.NoteImageRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteImageRepositoryImpl @Inject constructor(
    private val noteImageDao: NoteImageDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : NoteImageRepository {
    override fun observeNoteImages(noteId: String): Flow<List<NoteImageEntity>> =
        noteImageDao.observeNoteImages(noteId)

    override suspend fun getNoteImage(id: String): NoteImageEntity? = withContext(ioDispatcher) {
        noteImageDao.getById(id)
    }

    override suspend fun saveNoteImage(image: NoteImageEntity) = withContext(ioDispatcher) {
        noteImageDao.upsert(image)
    }

    override suspend fun saveNoteImages(images: List<NoteImageEntity>) = withContext(ioDispatcher) {
        noteImageDao.upsertAll(images)
    }

    override suspend fun softDeleteNoteImage(id: String) = withContext(ioDispatcher) {
        noteImageDao.softDelete(id)
    }
}
