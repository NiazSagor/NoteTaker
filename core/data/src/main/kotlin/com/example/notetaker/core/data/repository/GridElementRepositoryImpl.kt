package com.example.notetaker.core.data.repository

import com.example.notetaker.core.data.db.dao.ConflictDao
import com.example.notetaker.core.data.db.dao.GridElementDao
import com.example.notetaker.core.data.db.entity.ConflictEntity
import com.example.notetaker.core.data.db.entity.GridElementEntity
import com.example.notetaker.core.domain.conflict.ConflictDetector
import com.example.notetaker.core.domain.conflict.ConflictType
import com.example.notetaker.core.domain.di.IoDispatcher
import com.example.notetaker.core.domain.repository.ConflictRepository
import com.example.notetaker.core.domain.repository.GridElementRepository
import com.example.notetaker.core.network.firebase.FirestoreSource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GridElementRepositoryImpl @Inject constructor(
    private val gridElementDao: GridElementDao,
    private val conflictDao: ConflictDao,
    private val firestoreSource: FirestoreSource,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val appScope: CoroutineScope // Inject application-scoped CoroutineScope
) : GridElementRepository {

    private val workspaceId = "global_workspace"

    init {
        observeRemoteGridElements()
    }

    override fun observeGridElements(workspaceId: String): Flow<List<GridElementEntity>> {
        // Observes local Room data. Remote changes are synced to Room.
        return gridElementDao.observeGridElements(workspaceId)
    }

    override suspend fun getGridElement(id: String): GridElementEntity? = withContext(ioDispatcher) {
        gridElementDao.getById(id)
    }

    override suspend fun saveGridElement(element: GridElementEntity) = withContext(ioDispatcher) {
        // Local save first (optimistic update)
        gridElementDao.upsert(element)
        // Remote save is handled by sync mechanism (WorkManager)
    }

    override suspend fun softDeleteGridElement(id: String) = withContext(ioDispatcher) {
        gridElementDao.softDelete(id)
    }

    private fun observeRemoteGridElements() {
        // Launch the observation in the application's scope
        appScope.launch {
            flow {
                emitAll(firestoreSource.observeGridElements(workspaceId))
            }
            .flowOn(ioDispatcher)
            .onEach { remoteElements ->
                remoteElements.forEach { remoteElement ->
                    val localElement = gridElementDao.getById(remoteElement.id)

                    if (localElement != null) {
                        val conflictType = ConflictDetector.detect(
                            localVersion = localElement.localVersion,
                            remoteVersionAtLocal = localElement.remoteVersion,
                            incomingRemoteVersion = remoteElement.remoteVersion
                        )

                        when (conflictType) {
                            ConflictType.REMOTE_ADVANCED -> {
                                // Case 1: Safe to apply remote changes directly.
                                val updatedLocalElement = remoteElement.copy(
                                    localVersion = localElement.localVersion,
                                    syncStatus = localElement.syncStatus
                                )
                                gridElementDao.upsert(updatedLocalElement)
                            }
                            ConflictType.LOCAL_ADVANCED -> {
                                // Case 2: Local change is ahead. Do nothing.
                            }
                            ConflictType.CLEAN_FAST_FORWARD -> {
                                // Case 3: Remote advanced exactly one version. Safe to apply.
                                val updatedLocalElement = remoteElement.copy(
                                    localVersion = localElement.localVersion,
                                    syncStatus = localElement.syncStatus
                                )
                                gridElementDao.upsert(updatedLocalElement)
                            }
                            ConflictType.TRUE_CONFLICT -> {
                                // Case 4: Both local and remote diverged.
                                val conflict = ConflictEntity(
                                    id = UUID.randomUUID().toString(),
                                    noteId = remoteElement.noteId ?: "", // GridElement might not have noteId directly, needs mapping
                                    workspaceId = workspaceId,
                                    localSnapshot = gridElementDao.convertGridElementEntityToJson(localElement) ?: "{}", // Add null safety or default
                                    remoteSnapshot = gridElementDao.convertGridElementEntityToJson(remoteElement) ?: "{}", // Add null safety or default
                                    localVersion = localElement.localVersion,
                                    remoteVersion = remoteElement.remoteVersion,
                                    detectedAt = System.currentTimeMillis(),
                                    isResolved = false
                                )
                                conflictDao.upsert(conflict)
                                gridElementDao.updateSyncStatus(remoteElement.id, SyncStatus.CONFLICT)
                            }
                        }
                    } else {
                        // Local element doesn't exist, but remote does.
                        // Handle new remote elements.
                        if (!remoteElement.isDeleted) {
                            gridElementDao.upsert(remoteElement.copy(syncStatus = SyncStatus.SYNCED))
                        }
                    }
                }
            }
            .catch { e ->
                // Proper error handling for the flow
                // Log.e("GridElementSync", "Error observing remote grid elements", e)
            }
            .launchIn(appScope) // Use the injected application scope
        }
    }
}
