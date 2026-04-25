package com.example.notetaker.core.data.repository

import com.example.notetaker.core.data.db.dao.ConflictDao
import com.example.notetaker.core.data.db.entity.ConflictEntity
import com.example.notetaker.core.domain.di.IoDispatcher
import com.example.notetaker.core.domain.model.ResolutionStrategy
import com.example.notetaker.core.domain.repository.ConflictRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConflictRepositoryImpl @Inject constructor(
    private val conflictDao: ConflictDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ConflictRepository {
    override fun observeUnresolvedConflicts(workspaceId: String): Flow<List<ConflictEntity>> =
        conflictDao.observeUnresolvedConflicts(workspaceId)

    override fun observeConflictsForNote(noteId: String): Flow<List<ConflictEntity>> =
        conflictDao.observeConflictsForNote(noteId)

    override suspend fun getConflict(id: String): ConflictEntity? = withContext(ioDispatcher) {
        conflictDao.getById(id)
    }

    override suspend fun saveConflict(conflict: ConflictEntity) = withContext(ioDispatcher) {
        conflictDao.upsert(conflict)
    }

    override suspend fun resolveConflict(id: String, strategy: ResolutionStrategy, userId: String) = withContext(ioDispatcher) {
        conflictDao.markResolved(
            id = id,
            resolvedAt = System.currentTimeMillis(),
            strategy = strategy.name,
            userId = userId
        )
    }
}
