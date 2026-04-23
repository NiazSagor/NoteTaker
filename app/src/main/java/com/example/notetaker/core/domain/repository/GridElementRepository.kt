package com.example.notetaker.core.domain.repository

import com.example.notetaker.core.data.db.entity.GridElementEntity
import kotlinx.coroutines.flow.Flow

interface GridElementRepository {
    fun observeGridElements(workspaceId: String): Flow<List<GridElementEntity>>
    suspend fun getGridElement(id: String): GridElementEntity?
    suspend fun saveGridElement(element: GridElementEntity)
    suspend fun softDeleteGridElement(id: String)
}
