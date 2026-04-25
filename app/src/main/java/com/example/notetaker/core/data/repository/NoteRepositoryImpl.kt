package com.example.notetaker.core.data.repository

import android.util.Log
import com.example.notetaker.core.data.db.dao.ConflictDao
import com.example.notetaker.core.data.db.dao.NoteDao
import com.example.notetaker.core.data.db.entity.NoteEntity
import com.example.notetaker.core.data.sync.SyncManager
import com.example.notetaker.core.data.sync.SyncProcessor
import com.example.notetaker.core.domain.di.IoDispatcher
import com.example.notetaker.core.domain.model.Note
import com.example.notetaker.core.domain.repository.AuthRepository
import com.example.notetaker.core.domain.repository.NoteRepository
import com.example.notetaker.core.network.firebase.FirestoreSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
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
    private val syncManager: SyncManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val appScope: CoroutineScope // Injected application-scoped CoroutineScope
) : NoteRepository {

    private val workspaceId = "global_workspace" // Assuming a single global workspace
    private val TAG = "NoteRepositoryImpl"

    override fun observeNote(id: String): Flow<Note?> {
        observeRemoteNotes(id)
        return noteDao.observeNote(id).map { it?.toDomain() }
    }

    override suspend fun getNote(id: String): Note? = withContext(ioDispatcher) {
        noteDao.getNote(id)?.toDomain()
    }

    override suspend fun saveNote(note: NoteEntity) {
        // Local save first (optimistic update)
        noteDao.upsert(note)
        syncManager.syncNote(note.id)
        // TODO:  syncStatus appears = PENDING in firestore
    }

    override suspend fun softDeleteNote(id: String) {
        withContext(ioDispatcher) {
            noteDao.softDelete(id)
            syncManager.syncNote(id)
        }
    }

    private fun observeRemoteNotes(id: String) {
        appScope.launch {
            firestoreSource.observeNote(workspaceId, id)
                .flowOn(ioDispatcher)
                .onEach { remoteNote ->
                    remoteNote?.let {
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
