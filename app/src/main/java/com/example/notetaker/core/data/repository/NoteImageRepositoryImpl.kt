package com.example.notetaker.core.data.repository

import android.util.Log
import com.example.notetaker.core.data.db.dao.ConflictDao
import com.example.notetaker.core.data.db.dao.NoteImageDao
import com.example.notetaker.core.data.db.entity.NoteImageEntity
import com.example.notetaker.core.data.sync.SyncProcessor
import com.example.notetaker.core.domain.di.IoDispatcher
import com.example.notetaker.core.domain.model.SyncStatus
import com.example.notetaker.core.domain.repository.NoteImageRepository
import com.example.notetaker.core.network.firebase.FirestoreSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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

    override suspend fun saveNoteImage(image: NoteImageEntity) {
        withContext(ioDispatcher) {
            // Local save first (optimistic update)
            noteImageDao.upsert(image)

            // Immediate remote attempt
            try {
                firestoreSource.upsertNoteImage(workspaceId, image.noteId, image)
                noteImageDao.updateSyncStatus(image.id, SyncStatus.SYNCED)
            } catch (e: Exception) {
                Log.e(TAG, "Failed immediate sync for note image ${image.id}", e)
            }
        }
    }

    override suspend fun saveNoteImages(images: List<NoteImageEntity>) {
        withContext(ioDispatcher) {
            // Local save first
            noteImageDao.upsertAll(images)

            // Immediate remote attempt for each
            images.forEach { image ->
                try {
                    firestoreSource.upsertNoteImage(workspaceId, image.noteId, image)
                    noteImageDao.updateSyncStatus(image.id, SyncStatus.SYNCED)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed immediate sync for note image ${image.id} in batch", e)
                }
            }
        }
    }

    override suspend fun softDeleteNoteImage(id: String) {
        withContext(ioDispatcher) {
            noteImageDao.softDelete(id)

            // Immediate remote attempt
            try {
                val image = noteImageDao.getById(id)
                if (image != null) {
                    firestoreSource.upsertNoteImage(workspaceId, image.noteId, image)
                    noteImageDao.updateSyncStatus(id, SyncStatus.SYNCED)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed immediate sync for soft delete of note image $id", e)
            }
        }
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
