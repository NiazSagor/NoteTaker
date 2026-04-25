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
    val noteId: String, // Needed if repository.saveNote requires it, or for clarity
    val resolutionStrategy: ResolutionStrategy,
    val userId: String // The user who is resolving the conflict
)

class ResolveConflictUseCase @Inject constructor(
    private val conflictRepository: ConflictRepository,
    private val noteRepository: NoteRepository,
    @IoDispatcher dispatcher: CoroutineDispatcher
) : UseCase<ResolveConflictParams, Unit>(dispatcher) {
    override suspend fun execute(parameters: ResolveConflictParams) {
        val conflict = conflictRepository.getConflict(parameters.conflictId)
            ?: throw Exception("Conflict not found for ID: ${parameters.conflictId}")

        val noteToUpdate: NoteEntity // We need to convert domain Note back to Entity for repository save
        val resolvedBy = parameters.userId

        when (parameters.resolutionStrategy) {
            ResolutionStrategy.KEEP_LOCAL -> {
                // Load the local snapshot as NoteEntity and update it
                val localNote =
                    conflict.localNote?.toEntity() // Convert domain model back to entity
                        ?: throw Exception("Local snapshot not available for conflict ${conflict.noteId}")
                noteToUpdate =
                    localNote.copy(syncStatus = SyncStatus.PENDING) // Mark for sync after resolution
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
