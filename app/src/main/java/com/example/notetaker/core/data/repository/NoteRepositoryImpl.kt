package com.example.notetaker.core.data.repository

import android.util.Log
import com.example.notetaker.core.data.db.dao.ConflictDao
import com.example.notetaker.core.data.db.dao.NoteDao
import com.example.notetaker.core.data.db.entity.NoteEntity
import com.example.notetaker.core.data.sync.SyncProcessor
import com.example.notetaker.core.domain.di.IoDispatcher
import com.example.notetaker.core.domain.model.SyncStatus
import com.example.notetaker.core.domain.repository.AuthRepository
import com.example.notetaker.core.domain.repository.NoteRepository
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
class NoteRepositoryImpl @Inject constructor(
    private val noteDao: NoteDao,
    private val conflictDao: ConflictDao,
    private val firestoreSource: FirestoreSource,
    private val authRepository: AuthRepository,
    private val syncProcessor: SyncProcessor,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val appScope: CoroutineScope // Injected application-scoped CoroutineScope
) : NoteRepository {

    private val workspaceId = "global_workspace" // Assuming a single global workspace
    private val TAG = "NoteRepositoryImpl"

    init {
        observeRemoteNotes()
    }

    override fun observeNote(id: String): Flow<NoteEntity?> {
        return noteDao.observeNote(id)
    }

    override suspend fun getNote(id: String): NoteEntity? = withContext(ioDispatcher) {
        noteDao.getNote(id)
    }

    override suspend fun saveNote(note: NoteEntity) {
        withContext(ioDispatcher) {
            // Local save first (optimistic update)
            noteDao.upsert(note)

            // Immediate remote attempt
            try {
                firestoreSource.upsertNote(workspaceId, note)
                noteDao.updateSyncStatus(note.id, SyncStatus.SYNCED)
            } catch (e: Exception) {
                Log.e(TAG, "Failed immediate sync for note ${note.id}, will retry via WorkManager", e)
            }
        }
    }

    override suspend fun softDeleteNote(id: String) {
        withContext(ioDispatcher) {
            noteDao.softDelete(id)

            // Immediate remote attempt for soft delete (tombstone)
            try {
                val note = noteDao.getNote(id)
                if (note != null) {
                    firestoreSource.upsertNote(workspaceId, note)
                    noteDao.updateSyncStatus(id, SyncStatus.SYNCED)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed immediate sync for soft delete of note $id", e)
            }
        }
    }

    private fun observeRemoteNotes() {
        appScope.launch {
            firestoreSource.observeNotes(workspaceId)
                .flowOn(ioDispatcher)
                .onEach { remoteNotes ->
                    remoteNotes.forEach { remoteNote ->
                        syncProcessor.syncRemoteNote(remoteNote)
                    }
                }
                .catch { e ->
                    Log.e(TAG, "Error observing remote notes", e)
                }
                .launchIn(appScope)
        }
    }
}
