package com.example.notetaker.core.domain.repository

import com.example.notetaker.core.data.db.entity.GridElementEntity
import com.example.notetaker.core.domain.model.GridElement
import com.example.notetaker.core.domain.model.GridElementWithContent
import kotlinx.coroutines.flow.Flow

interface GridElementRepository {
    fun observeGridElementsWithContent(workspaceId: String): Flow<List<GridElementWithContent>>
    suspend fun getGridElement(id: String): GridElement?
    suspend fun saveGridElement(element: GridElementEntity)
    suspend fun softDeleteGridElement(id: String)
}
