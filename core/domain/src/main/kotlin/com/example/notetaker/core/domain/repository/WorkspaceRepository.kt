package com.example.notetaker.core.domain.repository

import com.example.notetaker.core.data.db.entity.WorkspaceEntity
import kotlinx.coroutines.flow.Flow

interface WorkspaceRepository {
    fun observeWorkspace(id: String): Flow<WorkspaceEntity?>
    suspend fun getWorkspace(id: String): WorkspaceEntity?
    suspend fun saveWorkspace(workspace: WorkspaceEntity)
}
