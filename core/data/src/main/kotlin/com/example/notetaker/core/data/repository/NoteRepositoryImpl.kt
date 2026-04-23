package com.example.notetaker.core.data.repository

import com.example.notetaker.core.data.db.dao.ConflictDao
import com.example.notetaker.core.data.db.dao.NoteDao
import com.example.notetaker.core.data.db.entity.ConflictEntity
import com.example.notetaker.core.data.db.entity.NoteEntity
import com.example.notetaker.core.domain.base.Result
import com.example.notetaker.core.domain.conflict.ConflictDetector
import com.example.notetaker.core.domain.conflict.ConflictType
import com.example.notetaker.core.domain.di.IoDispatcher
import com.example.notetaker.core.domain.repository.AuthRepository
import com.example.notetaker.core.domain.repository.ConflictRepository
import com.example.notetaker.core.domain.repository.NoteRepository
import com.example.notetaker.core.network.firebase.FirestoreSource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await // Import for await()
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepositoryImpl @Inject constructor(
    private val noteDao: NoteDao,
    private val conflictDao: ConflictDao,
    private val firestoreSource: FirestoreSource,
    private val authRepository: AuthRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val appScope: CoroutineScope // Inject application-scoped CoroutineScope
) : NoteRepository {

    private val workspaceId = "global_workspace"

    init {
        observeRemoteNotes() // This call will use the repository's scope
    }

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

    private fun observeRemoteNotes() {
        // Launch the observation in the application's scope
        appScope.launch {
            flow {
                emitAll(firestoreSource.observeNotes(workspaceId))
            }
            .flowOn(ioDispatcher)
            .onEach { remoteNotes ->
                remoteNotes.forEach { remoteNote ->
                    val localNote = noteDao.getNote(remoteNote.id)

                    if (localNote != null) {
                        val conflictType = ConflictDetector.detect(
                            localVersion = localNote.localVersion,
                            remoteVersionAtLocal = localNote.remoteVersion,
                            incomingRemoteVersion = remoteNote.remoteVersion
                        )

                        when (conflictType) {
                            ConflictType.REMOTE_ADVANCED -> {
                                val updatedLocalNote = remoteNote.copy(
                                    localVersion = localNote.localVersion,
                                    syncStatus = localNote.syncStatus
                                )
                                noteDao.upsert(updatedLocalNote)
                            }
                            ConflictType.LOCAL_ADVANCED -> { /* Do nothing */ }
                            ConflictType.CLEAN_FAST_FORWARD -> {
                                val updatedLocalNote = remoteNote.copy(
                                    localVersion = localNote.localVersion,
                                    syncStatus = localNote.syncStatus
                                )
                                noteDao.upsert(updatedLocalNote)
                            }
                            ConflictType.TRUE_CONFLICT -> {
                                val conflict = ConflictEntity(
                                    id = UUID.randomUUID().toString(),
                                    noteId = remoteNote.id,
                                    workspaceId = workspaceId,
                                    localSnapshot = noteDao.convertNoteEntityToJson(localNote) ?: "{}", // Add null safety or default
                                    remoteSnapshot = noteDao.convertNoteEntityToJson(remoteNote) ?: "{}", // Add null safety or default
                                    localVersion = localNote.localVersion,
                                    remoteVersion = remoteNote.remoteVersion,
                                    detectedAt = System.currentTimeMillis(),
                                    isResolved = false
                                )
                                conflictDao.upsert(conflict)
                                noteDao.updateSyncStatus(remoteNote.id, SyncStatus.CONFLICT)
                            }
                        }
                    } else {
                        // Local note doesn't exist, handle new remote notes.
                        if (!remoteNote.isDeleted) {
                            noteDao.upsert(remoteNote.copy(syncStatus = SyncStatus.SYNCED))
                        }
                    }
                }
            }
            .catch { e ->
                // Proper error handling for the flow
                // Log.e("NoteSync", "Error observing remote notes", e)
                // Potentially update a sync error status if needed
            }
            .launchIn(appScope) // Use the injected application scope
        }
    }
}
