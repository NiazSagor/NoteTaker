package com.example.notetaker.core.data.db.dao

import androidx.room.*
import com.example.notetaker.core.data.db.entity.ConflictEntity
import com.example.notetaker.core.domain.model.ResolutionStrategy
import kotlinx.coroutines.flow.Flow

@Dao
interface ConflictDao {
    @Query("SELECT * FROM conflicts WHERE workspaceId = :workspaceId AND isResolved = 0")
    fun observeUnresolvedConflicts(workspaceId: String): Flow<List<ConflictEntity>>

    @Query("SELECT * FROM conflicts WHERE noteId = :noteId AND isResolved = 0")
    fun observeConflictsForNote(noteId: String): Flow<List<ConflictEntity>>

    @Query("SELECT * FROM conflicts WHERE id = :id")
    suspend fun getById(id: String): ConflictEntity?

    @Upsert
    suspend fun upsert(conflict: ConflictEntity)

    @Transaction
    @Query("UPDATE conflicts SET isResolved = 1, resolvedAt = :resolvedAt, resolutionStrategy = :strategy, resolvedBy = :userId WHERE id = :id")
    suspend fun markResolved(id: String, resolvedAt: Long, strategy: String, userId: String)

    // Add method to update syncStatus if applicable to ConflictEntity, though typically syncStatus is on the entity being conflicted.
    // For now, ConflictEntity itself doesn't have syncStatus directly, but its resolution updates its isResolved flag.
    // If ConflictEntity itself needed a syncStatus, it would be added here.
}
