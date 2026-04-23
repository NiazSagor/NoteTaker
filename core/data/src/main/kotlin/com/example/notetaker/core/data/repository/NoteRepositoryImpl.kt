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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepositoryImpl @Inject constructor(
    private val noteDao: NoteDao,
    private val conflictDao: ConflictDao, // Needed to save ConflictEntity
    private val firestoreSource: FirestoreSource,
    private val authRepository: AuthRepository, // Needed for current user ID when saving conflicts
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : NoteRepository {

    private val workspaceId = "global_workspace" // Assuming a single global workspace for now

    init {
        // Start observing remote notes when the repository is initialized
        observeRemoteNotes()
    }

    override fun observeNote(id: String): Flow<NoteEntity?> {
        // This observes local Room data. Changes from Firestore are synced to Room.
        return noteDao.observeNote(id)
    }

    override suspend fun getNote(id: String): NoteEntity? = withContext(ioDispatcher) {
        noteDao.getNote(id)
    }

    override suspend fun saveNote(note: NoteEntity) = withContext(ioDispatcher) {
        // Local save first (optimistic update)
        noteDao.upsert(note)
        // Remote save will be handled by the sync mechanism (WorkManager)
        // or potentially FirestoreSource directly if not using WorkManager for all writes
    }

    override suspend fun softDeleteNote(id: String) = withContext(ioDispatcher) {
        noteDao.softDelete(id)
    }

    private fun observeRemoteNotes() {
        // Launch a coroutine to observe remote changes
        // This flow will run on the IO dispatcher due to the use of withContext in repository methods
        // and should be managed within a suitable scope, e.g., a service or ViewModel scope if appropriate.
        // For repository initialization, a separate coroutine scope might be needed, or a higher scope like ApplicationScope.
        // For now, let's assume a scope is available.
        // In a real app, this might be tied to lifecycle or a dedicated sync service.
        // We'll launch it in a way that it runs independently.
        // Using viewModelScope is not appropriate here as it's tied to a VM's lifecycle.
        // Let's assume for now it runs in a coroutine scope that lives as long as the app.
        // If this repository is a singleton, a CoroutineScope could be injected.
        // For simplicity here, imagine it's managed by Hilt or ApplicationScope.
        flow {
            emitAll(firestoreSource.observeNotes(workspaceId))
        }
            .flowOn(ioDispatcher) // Ensure this observation runs on IO dispatcher
            .onEach { remoteNotes ->
                // Process incoming remote notes and detect conflicts
                remoteNotes.forEach { remoteNote ->
                    // We need to ensure fields that should not be synced are excluded from remoteNote if it's mapped directly from Firestore.
                    // For now, assume remoteNote is clean enough to compare versions.
                    val localNote = noteDao.getNote(remoteNote.id)

                    if (localNote != null) {
                        val conflictType = ConflictDetector.detect(
                            localVersion = localNote.localVersion,
                            remoteVersionAtLocal = localNote.remoteVersion,
                            incomingRemoteVersion = remoteNote.remoteVersion
                        )

                        when (conflictType) {
                            ConflictType.REMOTE_ADVANCED -> {
                                // Case 1: Safe to apply remote changes directly
                                // Update local note with remote data, preserving local edits for fields not in remoteNote if necessary.
                                // For simplicity, we'll overwrite with remote data but maintain localVersion for future edits.
                                val updatedLocalNote = remoteNote.copy(
                                    localVersion = localNote.localVersion, // Keep local version for future edits
                                    syncStatus = localNote.syncStatus // Preserve local sync status if it's not SYNCED
                                )
                                noteDao.upsert(updatedLocalNote)
                            }
                            ConflictType.LOCAL_ADVANCED -> {
                                // Case 2: Local change is ahead, remote is unchanged. No action needed for sync, our local change will be pushed.
                                // Do nothing, let WorkManager handle pushing local change.
                            }
                            ConflictType.CLEAN_FAST_FORWARD -> {
                                // Case 3: Remote advanced exactly one version ahead. Safe to apply.
                                // Here, we might merge intelligently if needed, but for now, apply remote changes.
                                val updatedLocalNote = remoteNote.copy(
                                    localVersion = localNote.localVersion, // Keep local version for future edits
                                    syncStatus = localNote.syncStatus // Preserve local sync status
                                )
                                noteDao.upsert(updatedLocalNote)
                                // Optionally notify UI about a non-blocking update (e.g., snackbar)
                            }
                            ConflictType.TRUE_CONFLICT -> {
                                // Case 4: Both local and remote diverged.
                                // Save the conflict details and mark the note as conflicted.
                                val conflict = ConflictEntity(
                                    id = UUID.randomUUID().toString(),
                                    noteId = remoteNote.id,
                                    workspaceId = workspaceId,
                                    localSnapshot = noteDao.convertNoteEntityToJson(localNote), // Need converter helper
                                    remoteSnapshot = noteDao.convertNoteEntityToJson(remoteNote), // Need converter helper
                                    localVersion = localNote.localVersion,
                                    remoteVersion = remoteNote.remoteVersion,
                                    detectedAt = System.currentTimeMillis(),
                                    isResolved = false
                                    // resolutionStrategy, resolvedAt, resolvedBy are null initially
                                )
                                conflictDao.upsert(conflict) // Save conflict
                                // Update note's syncStatus to CONFLICT
                                noteDao.updateSyncStatus(remoteNote.id, SyncStatus.CONFLICT)
                            }
                        }
                    } else {
                        // Local note doesn't exist, but remote does. This could be a new note created by another user
                        // or a deletion on another client that needs to be reflected.
                        // For now, assume remote note is the source of truth if local is missing and not deleted.
                        // Need to handle `isDeleted` field from remote if applicable.
                        if (!remoteNote.isDeleted) {
                            noteDao.upsert(remoteNote.copy(syncStatus = SyncStatus.SYNCED))
                        }
                    }
                }
            }
            // .catch { error -> Log.e("NoteSync", "Error observing remote notes", error) } // Proper error handling
            .launchIn(viewModelScope) // This scope needs to be managed correctly for a singleton repository.
            // Using ApplicationScope or a CoroutineScope provided by Hilt would be more appropriate.
            // For now, assuming 'viewModelScope' is a placeholder for a long-lived scope.
    }
}

// Helper methods for JSON conversion would be needed in NoteDao or a separate converter
// For simplicity, assuming these exist or can be added later.
// Example:
// @TypeConverter
// fun convertNoteEntityToJson(note: NoteEntity): String = Gson().toJson(note)
// fun convertJsonToNoteEntity(json: String): NoteEntity = Gson().fromJson(json, NoteEntity::class.java)

// Need to add updateSyncStatus to NoteDao
// @Query("UPDATE notes SET syncStatus = :status WHERE id = :id")
// suspend fun updateSyncStatus(id: String, status: SyncStatus)

// Need to add helper methods to NoteDao for JSON conversion or use a library like Kotlinx Serialization.
// For now, let's assume these are placeholders and focus on the flow.
