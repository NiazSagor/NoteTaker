package com.example.notetaker.core.data.db.dao

import androidx.room.*
import com.example.notetaker.core.data.db.entity.ConflictEntity
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

    @Query("UPDATE conflicts SET isResolved = 1, resolvedAt = :resolvedAt, resolutionStrategy = :strategy, resolvedBy = :userId WHERE id = :id")
    suspend fun markResolved(id: String, resolvedAt: Long, strategy: String, userId: String)
}
