package com.example.notetaker.core.data.repository

import android.util.Log
import com.example.notetaker.core.data.db.dao.GridElementDao
import com.example.notetaker.core.data.db.entity.GridElementEntity
import com.example.notetaker.core.data.sync.SyncManager
import com.example.notetaker.core.data.sync.SyncProcessor
import com.example.notetaker.core.domain.di.IoDispatcher
import com.example.notetaker.core.domain.model.GridElement
import com.example.notetaker.core.domain.model.GridElementWithContent
import com.example.notetaker.core.domain.repository.GridElementRepository
import com.example.notetaker.core.network.firebase.FirestoreSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GridElementRepositoryImpl @Inject constructor(
    private val gridElementDao: GridElementDao,
    private val firestoreSource: FirestoreSource,
    private val syncProcessor: SyncProcessor,
    private val syncManager: SyncManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val appScope: CoroutineScope
) : GridElementRepository {

    private val workspaceId = "global_workspace"

    init {
        observeRemoteGridElements()
    }

    override fun observeGridElementsWithContent(workspaceId: String): Flow<List<GridElementWithContent>> {
        return gridElementDao.observeGridElementsWithContent(workspaceId)
            .map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getGridElement(id: String): GridElement? =
        withContext(ioDispatcher) {
            gridElementDao.getById(id)?.toDomain()
        }

    override suspend fun saveGridElement(element: GridElementEntity) {
        withContext(ioDispatcher) {
            // Local save first
            gridElementDao.upsert(element)
            syncManager.syncGridElement()
        }
    }

    override suspend fun softDeleteGridElement(id: String) {
        withContext(ioDispatcher) {
            gridElementDao.softDelete(id)
            syncManager.syncGridElement()
        }
    }

    private fun observeRemoteGridElements() {
        appScope.launch {
            firestoreSource.observeGridElements(workspaceId)
                .flowOn(ioDispatcher)
                .onEach { remoteElements ->
                    remoteElements.forEach { remoteElement ->
                        syncProcessor.syncRemoteGridElement(remoteElement)
                    }
                }
                .catch { e ->
                    Log.e("GridRepository", "Error observing remote grid elements", e)
                }
                .launchIn(appScope)
        }
    }
}
