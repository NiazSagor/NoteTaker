package com.example.notetaker.core.data.repository

import com.example.notetaker.core.data.db.dao.WorkspaceDao
import com.example.notetaker.core.data.db.entity.WorkspaceEntity
import com.example.notetaker.core.domain.repository.WorkspaceRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkspaceRepositoryImpl @Inject constructor(
    private val workspaceDao: WorkspaceDao
) : WorkspaceRepository {
    override fun observeWorkspace(id: String): Flow<WorkspaceEntity?> {
        return workspaceDao.observeWorkspace(id)
    }

    override suspend fun getWorkspace(id: String): WorkspaceEntity? {
        return workspaceDao.getWorkspace(id)
    }

    override suspend fun saveWorkspace(workspace: WorkspaceEntity) {
        workspaceDao.upsert(workspace)
    }
}
