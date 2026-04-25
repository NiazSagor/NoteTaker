package com.example.notetaker.core.domain.repository

import com.example.notetaker.core.data.db.entity.WorkspaceEntity
import com.example.notetaker.core.domain.model.Workspace
import kotlinx.coroutines.flow.Flow

interface WorkspaceRepository {
    fun observeWorkspace(id: String): Flow<Workspace?>
    suspend fun getWorkspace(id: String): Workspace?
    suspend fun saveWorkspace(workspace: WorkspaceEntity)
}
