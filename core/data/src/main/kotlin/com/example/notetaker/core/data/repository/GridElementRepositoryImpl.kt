package com.example.notetaker.core.data.repository

import com.example.notetaker.core.data.db.dao.GridElementDao
import com.example.notetaker.core.data.db.entity.GridElementEntity
import com.example.notetaker.core.domain.di.IoDispatcher
import com.example.notetaker.core.domain.repository.GridElementRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GridElementRepositoryImpl @Inject constructor(
    private val gridElementDao: GridElementDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : GridElementRepository {
    override fun observeGridElements(workspaceId: String): Flow<List<GridElementEntity>> {
        return gridElementDao.observeGridElements(workspaceId)
    }

    override suspend fun getGridElement(id: String): GridElementEntity? = withContext(ioDispatcher) {
        gridElementDao.getById(id)
    }

    override suspend fun saveGridElement(element: GridElementEntity) = withContext(ioDispatcher) {
        gridElementDao.upsert(element)
    }

    override suspend fun softDeleteGridElement(id: String) = withContext(ioDispatcher) {
        gridElementDao.softDelete(id)
    }
}
