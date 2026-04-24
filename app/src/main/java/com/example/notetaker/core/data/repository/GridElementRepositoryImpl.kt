package com.example.notetaker.core.data.repository

import android.util.Log
import com.example.notetaker.core.data.db.dao.ConflictDao
import com.example.notetaker.core.data.db.dao.GridElementDao
import com.example.notetaker.core.data.db.entity.GridElementEntity
import com.example.notetaker.core.data.db.entity.GridElementWithContent
import com.example.notetaker.core.data.sync.SyncProcessor
import com.example.notetaker.core.domain.di.IoDispatcher
import com.example.notetaker.core.domain.repository.GridElementRepository
import com.example.notetaker.core.network.firebase.FirestoreSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GridElementRepositoryImpl @Inject constructor(
    private val gridElementDao: GridElementDao,
    private val conflictDao: ConflictDao,
    private val firestoreSource: FirestoreSource,
    private val syncProcessor: SyncProcessor,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val appScope: CoroutineScope // Inject application-scoped CoroutineScope
) : GridElementRepository {

    private val workspaceId = "global_workspace"
    private val TAG = "GridElementRepoImpl"

    init {
        observeRemoteGridElements()
    }

    override fun observeGridElementsWithContent(workspaceId: String): Flow<List<GridElementWithContent>> {
        return gridElementDao.observeGridElementsWithContent(workspaceId)
    }

    override suspend fun getGridElement(id: String): GridElementEntity? =
        withContext(ioDispatcher) {
            gridElementDao.getById(id)
        }

    override suspend fun saveGridElement(element: GridElementEntity) = withContext(ioDispatcher) {
        gridElementDao.upsert(element)
    }

    override suspend fun softDeleteGridElement(id: String) = withContext(ioDispatcher) {
        gridElementDao.softDelete(id)
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
                    Log.e(TAG, "Error observing remote grid elements", e)
                }
                .launchIn(appScope)
        }
    }
}
