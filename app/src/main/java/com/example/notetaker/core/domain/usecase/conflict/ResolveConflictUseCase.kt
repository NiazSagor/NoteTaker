package com.example.notetaker.core.domain.usecase.conflict

import com.example.notetaker.core.data.db.entity.NoteEntity
import com.example.notetaker.core.domain.base.UseCase
import com.example.notetaker.core.domain.di.IoDispatcher
import com.example.notetaker.core.domain.model.ResolutionStrategy
import com.example.notetaker.core.domain.model.SyncStatus
import com.example.notetaker.core.domain.repository.ConflictRepository
import com.example.notetaker.core.domain.repository.NoteRepository
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

data class ResolveConflictParams(
    val conflictId: String,
    val noteId: String,
    val resolutionStrategy: ResolutionStrategy,
    val userId: String
)

class ResolveConflictUseCase @Inject constructor(
    private val conflictRepository: ConflictRepository,
    private val noteRepository: NoteRepository,
    @IoDispatcher dispatcher: CoroutineDispatcher
) : UseCase<ResolveConflictParams, Unit>(dispatcher) {
    override suspend fun execute(parameters: ResolveConflictParams) {
        val conflict = conflictRepository.getConflict(parameters.conflictId)
            ?: throw Exception("Conflict not found for ID: ${parameters.conflictId}")

        val noteToUpdate: NoteEntity
        val resolvedBy = parameters.userId
        val conflictRemoteVersion = conflict.remoteVersion

        when (parameters.resolutionStrategy) {
            ResolutionStrategy.KEEP_LOCAL -> {
                // Load the local snapshot as NoteDomain model, convert to entity
                val localNote = conflict.localNote?.toEntity()
                    ?: throw Exception("Local snapshot not available for conflict ${conflict.noteId}")

                // When keeping local, we are effectively overwriting the remote state for this sync.
                // The local changes are now considered the source of truth.
                // We set syncStatus to PENDING to push this resolved local version.
                // Crucially, we should also update the remoteVersion in the local entity to match
                // the remote version that caused the conflict. This prevents immediate re-conflict
                // if the remote state hasn't changed further. The localVersion is incremented as it's a new change.
                noteToUpdate = localNote.copy(
                    syncStatus = SyncStatus.PENDING,
                    remoteVersion = conflictRemoteVersion, // Syncing based on the remote version we conflicted with
                    localVersion = localNote.localVersion + 1 // Increment local version for the new local resolution change
                )
            }

            ResolutionStrategy.KEEP_REMOTE -> {
                // Load the remote snapshot as NoteEntity and update it
                val remoteNote =
                    conflict.remoteNote?.toEntity() // Convert domain model back to entity
                        ?: throw Exception("Remote snapshot not available for conflict ${conflict.noteId}")
                noteToUpdate = remoteNote.copy(syncStatus = SyncStatus.SYNCED) // Mark as synced
            }
            // Add other strategies if needed, e.g., MERGE (complex)
            else -> {
                throw IllegalArgumentException("Unsupported resolution strategy: ${parameters.resolutionStrategy}")
            }
        }

        conflictRepository.resolveConflict(
            id = parameters.conflictId,
            strategy = parameters.resolutionStrategy,
            userId = resolvedBy
        )

        // Update the note in the database
        noteRepository.saveNote(noteToUpdate)
    }
}
