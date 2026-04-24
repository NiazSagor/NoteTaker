package com.example.notetaker.core.data.repository

import android.util.Log
import com.example.notetaker.core.data.db.dao.ConflictDao
import com.example.notetaker.core.data.db.dao.NoteImageDao
import com.example.notetaker.core.data.db.entity.NoteImageEntity
import com.example.notetaker.core.data.sync.SyncProcessor
import com.example.notetaker.core.domain.di.IoDispatcher
import com.example.notetaker.core.domain.repository.NoteImageRepository
import com.example.notetaker.core.network.firebase.FirestoreSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteImageRepositoryImpl @Inject constructor(
    private val noteImageDao: NoteImageDao,
    private val conflictDao: ConflictDao,
    private val firestoreSource: FirestoreSource,
    private val syncProcessor: SyncProcessor,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val appScope: CoroutineScope
) : NoteImageRepository {

    private val workspaceId = "global_workspace" // Assuming a single global workspace
    private val TAG = "NoteImageRepoImpl"

    override fun observeNoteImages(noteId: String): Flow<List<NoteImageEntity>> {
        // Trigger remote observation when a note is observed
        observeRemoteNoteImages(noteId)
        // Observes local Room data. Remote changes are synced to Room.
        return noteImageDao.observeNoteImages(noteId)
    }

    override suspend fun getNoteImage(id: String): NoteImageEntity? = withContext(ioDispatcher) {
        noteImageDao.getById(id)
    }

    override suspend fun saveNoteImage(image: NoteImageEntity) = withContext(ioDispatcher) {
        // Local save first (optimistic update)
        noteImageDao.upsert(image)
        // Remote save handled by sync mechanism
    }

    override suspend fun saveNoteImages(images: List<NoteImageEntity>) = withContext(ioDispatcher) {
        // Local save first
        noteImageDao.upsertAll(images)
        // Remote save handled by sync mechanism
    }

    override suspend fun softDeleteNoteImage(id: String) = withContext(ioDispatcher) {
        noteImageDao.softDelete(id)
    }

    private fun observeRemoteNoteImages(noteId: String) {
        appScope.launch {
            firestoreSource.observeNoteImages(workspaceId, noteId)
                .flowOn(ioDispatcher)
                .onEach { remoteImages ->
                    remoteImages.forEach { remoteImage ->
                        syncProcessor.syncRemoteNoteImage(remoteImage, noteId)
                    }
                }
                .catch { e ->
                    Log.e(TAG, "Error observing remote note images for note $noteId", e)
                }
                .launchIn(appScope)
        }
    }
}
