package com.example.notetaker.core.data.repository

import com.example.notetaker.core.data.db.dao.GridElementDao
import com.example.notetaker.core.data.db.entity.GridElementEntity
import com.example.notetaker.core.domain.repository.GridElementRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GridElementRepositoryImpl @Inject constructor(
    private val gridElementDao: GridElementDao
) : GridElementRepository {
    override fun observeGridElements(workspaceId: String): Flow<List<GridElementEntity>> {
        return gridElementDao.observeGridElements(workspaceId)
    }

    override suspend fun getGridElement(id: String): GridElementEntity? {
        return gridElementDao.getById(id)
    }

    override suspend fun saveGridElement(element: GridElementEntity) {
        gridElementDao.upsert(element)
    }

    override suspend fun softDeleteGridElement(id: String) {
        gridElementDao.softDelete(id)
    }
}
