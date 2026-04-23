package com.example.notetaker.core.data.repository

import com.example.notetaker.core.data.db.dao.WorkspaceDao
import com.example.notetaker.core.data.db.entity.WorkspaceEntity
import com.example.notetaker.core.domain.di.IoDispatcher
import com.example.notetaker.core.domain.repository.WorkspaceRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkspaceRepositoryImpl @Inject constructor(
    private val workspaceDao: WorkspaceDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : WorkspaceRepository {
    override fun observeWorkspace(id: String): Flow<WorkspaceEntity?> {
        return workspaceDao.observeWorkspace(id)
    }

    override suspend fun getWorkspace(id: String): WorkspaceEntity? = withContext(ioDispatcher) {
        workspaceDao.getWorkspace(id)
    }

    override suspend fun saveWorkspace(workspace: WorkspaceEntity) = withContext(ioDispatcher) {
        workspaceDao.upsert(workspace)
    }
}
