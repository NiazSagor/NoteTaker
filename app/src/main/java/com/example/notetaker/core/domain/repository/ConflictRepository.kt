package com.example.notetaker.core.domain.repository

import com.example.notetaker.core.data.db.entity.ConflictEntity
import com.example.notetaker.core.domain.model.Conflict
import com.example.notetaker.core.domain.model.ResolutionStrategy
import kotlinx.coroutines.flow.Flow

interface ConflictRepository {
    fun observeUnresolvedConflicts(workspaceId: String): Flow<List<Conflict>>
    fun observeConflictsForNote(noteId: String): Flow<List<Conflict>>
    suspend fun getConflict(id: String): Conflict?
    suspend fun saveConflict(conflict: ConflictEntity)
    suspend fun resolveConflict(id: String, strategy: ResolutionStrategy, userId: String)
}
