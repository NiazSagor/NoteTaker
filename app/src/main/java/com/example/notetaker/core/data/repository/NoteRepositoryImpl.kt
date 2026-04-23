package com.example.notetaker.core.data.repository

import com.example.notetaker.core.data.db.dao.ConflictDao
import com.example.notetaker.core.data.db.dao.NoteDao
import com.example.notetaker.core.data.db.entity.NoteEntity
import com.example.notetaker.core.domain.di.IoDispatcher
import com.example.notetaker.core.domain.repository.AuthRepository
import com.example.notetaker.core.domain.repository.NoteRepository
import com.example.notetaker.core.network.firebase.FirestoreSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepositoryImpl @Inject constructor(
    private val noteDao: NoteDao,
    private val conflictDao: ConflictDao,
    private val firestoreSource: FirestoreSource,
    private val authRepository: AuthRepository,
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

    override suspend fun saveNote(note: NoteEntity) = withContext(ioDispatcher) {
        // Local save first (optimistic update)
        noteDao.upsert(note)
        // Remote save is handled by SyncPendingWorker
    }

    override suspend fun softDeleteNote(id: String) = withContext(ioDispatcher) {
        noteDao.softDelete(id)
        // Remote deletion will be handled by SyncPendingWorker
    }

    private fun observeRemoteNotes() {
//        appScope.launch {
//            firestoreSource.observeNotes(workspaceId)
//                .flowOn(ioDispatcher)
//                .onEach { remoteNotes ->
//                    remoteNotes.forEach { remoteNote ->
//                        // Skip processing if the remote note is marked as deleted, unless it's a tombstone we need to locally delete.
//                        if (remoteNote.isDeleted) {
//                            // If remote says deleted, ensure local is deleted or marked as deleted.
//                            // This might involve checking local state and deleting/marking local entity.
//                            // For now, we assume a deletion means local entity should also be deleted/marked.
//                            noteDao.deleteById(remoteNote.id) // Assuming deleteById method exists in NoteDao
//                            return@forEach // Skip conflict check if deleted remotely and we handle deletion locally
//                        }
//
//                        val localNote = noteDao.getNote(remoteNote.id)
//
//                        if (localNote != null) {
//                            // Local entity exists, perform conflict detection
//                            val conflictType = ConflictDetector.detect(
//                                localVersion = localNote.localVersion,
//                                remoteVersionAtLocal = localNote.remoteVersion,
//                                incomingRemoteVersion = remoteNote.remoteVersion
//                            )
//
//                            when (conflictType) {
//                                ConflictType.REMOTE_ADVANCED -> {
//                                    // Case 1: Safe to apply remote changes directly.
//                                    // Apply remote data, preserve localVersion for future edits.
//                                    val updatedLocalNote = remoteNote.copy(
//                                        localVersion = localNote.localVersion,
//                                        syncStatus = localNote.syncStatus // Preserve local sync status if it's not SYNCED
//                                    )
//                                    noteDao.upsert(updatedLocalNote)
//                                }
//                                ConflictType.LOCAL_ADVANCED -> {
//                                    // Case 2: Local change is ahead. Do nothing here.
//                                    // Our local change will be pushed by SyncPendingWorker.
//                                }
//                                ConflictType.CLEAN_FAST_FORWARD -> {
//                                    // Case 3: Remote advanced exactly one version. Safe to apply.
//                                    // SKILL.md suggests "append remote change, keep unsaved local draft".
//                                    // For simplicity, we'll merge by applying remote data and keeping localVersion.
//                                    // A more complex merge strategy might be needed depending on data structure.
//                                    val updatedLocalNote = remoteNote.copy(
//                                        localVersion = localNote.localVersion,
//                                        syncStatus = localNote.syncStatus // Preserve local sync status
//                                    )
//                                    noteDao.upsert(updatedLocalNote)
//                                    // Optionally notify UI about a non-blocking update (e.g., snackbar)
//                                }
//                                ConflictType.TRUE_CONFLICT -> {
//                                    // Case 4: Both local and remote diverged.
//                                    val conflict = ConflictEntity(
//                                        id = UUID.randomUUID().toString(),
//                                        noteId = remoteNote.id,
//                                        workspaceId = workspaceId,
//                                        localSnapshot = noteDao.convertNoteEntityToJson(localNote),
//                                        remoteSnapshot = noteDao.convertNoteEntityToJson(remoteNote),
//                                        localVersion = localNote.localVersion,
//                                        remoteVersion = remoteNote.remoteVersion,
//                                        detectedAt = System.currentTimeMillis(),
//                                        isResolved = false
//                                    )
//                                    conflictDao.upsert(conflict)
//                                    noteDao.updateSyncStatus(remoteNote.id, SyncStatus.CONFLICT)
//                                }
//                            }
//                        } else {
//                            // Local note doesn't exist, but remote does.
//                            // This could be a new note created by another user or a deletion.
//                            if (!remoteNote.isDeleted) {
//                                // If remote note is not deleted, insert it locally and mark as SYNCED.
//                                noteDao.upsert(remoteNote.copy(syncStatus = SyncStatus.SYNCED))
//                            } else {
//                                // If remote note is marked deleted, ensure it's deleted locally too.
//                                // This is already handled by softDelete if the change came from remote,
//                                // or it might need explicit deletion if the remote change is just 'isDeleted = true'.
//                                // For now, assuming remote delete means local should reflect that.
//                                // If local entity exists and isDeleted is false, it should be deleted.
//                                // If remote is deleted, ensure local is marked as deleted.
//                                //noteDao.deleteById(remoteNote.id) // Assuming deleteById method exists in NoteDao
//                            }
//                        }
//                    }
//                }
//            }
//            .catch { e ->
//                Log.e(TAG, "Error observing remote notes", e)
//                // Potentially update a sync error status if needed for the repository itself
//            }
//            .launchIn(appScope) // Use the injected application scope
    }
}


// Note: The following DAO methods are assumed to exist and need to be implemented in NoteDao:
// - deleteById(id: String)
// - convertNoteEntityToJson(note: NoteEntity): String (already implemented using kotlinx.serialization)
// - The primary task here is ensuring the logic for handling conflict detection and updates is correctly placed.
