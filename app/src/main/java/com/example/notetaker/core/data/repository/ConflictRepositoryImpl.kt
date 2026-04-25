package com.example.notetaker.core.data.repository

import com.example.notetaker.core.data.db.dao.ConflictDao
import com.example.notetaker.core.data.db.entity.ConflictEntity
import com.example.notetaker.core.domain.di.IoDispatcher
import com.example.notetaker.core.domain.model.Conflict
import com.example.notetaker.core.domain.model.ResolutionStrategy
import com.example.notetaker.core.domain.repository.ConflictRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConflictRepositoryImpl @Inject constructor(
    private val conflictDao: ConflictDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ConflictRepository {

    override fun observeUnresolvedConflicts(workspaceId: String): Flow<List<Conflict>> {
        return conflictDao.observeUnresolvedConflicts(workspaceId)
            .map { conflicts -> conflicts.map { it.toDomain() } }
    }

    override fun observeConflictsForNote(noteId: String): Flow<List<Conflict>> {
        return conflictDao.observeConflictsForNote(noteId)
            .map { conflicts -> conflicts.map { it.toDomain() } }
    }

    override suspend fun getConflict(id: String): Conflict? = withContext(ioDispatcher) {
        conflictDao.getById(id)?.toDomain()
    }

    override suspend fun saveConflict(conflict: ConflictEntity) {
        withContext(ioDispatcher) {
            conflictDao.upsert(conflict)
        }
    }

    override suspend fun resolveConflict(id: String, strategy: ResolutionStrategy, userId: String) {
        withContext(ioDispatcher) {
            conflictDao.markResolved(
                id = id,
                resolvedAt = System.currentTimeMillis(),
                strategy = strategy.name,
                userId = userId
            )
        }
    }
}
