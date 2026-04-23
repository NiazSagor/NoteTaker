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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GridElementRepositoryImpl @Inject constructor(
    private val gridElementDao: GridElementDao,
    private val conflictDao: ConflictDao,
    private val firestoreSource: FirestoreSource,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : GridElementRepository {

    private val workspaceId = "global_workspace" // Assuming a single global workspace

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
        // Remote save is handled by WorkManager or FirestoreSource directly
    }

    override suspend fun softDeleteGridElement(id: String) = withContext(ioDispatcher) {
        gridElementDao.softDelete(id)
    }

    private fun observeRemoteGridElements() {
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
                                // Preserve localVersion for future edits.
                                val updatedLocalElement = remoteElement.copy(
                                    localVersion = localElement.localVersion,
                                    syncStatus = localElement.syncStatus // Preserve local sync status if not SYNCED
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
                                    localSnapshot = gridElementDao.convertGridElementEntityToJson(localElement), // Need converter
                                    remoteSnapshot = gridElementDao.convertGridElementEntityToJson(remoteElement), // Need converter
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
                        // Handle new remote elements (e.g., created by another user).
                        if (!remoteElement.isDeleted) {
                            gridElementDao.upsert(remoteElement.copy(syncStatus = SyncStatus.SYNCED))
                        }
                    }
                }
            }
            .launchIn(viewModelScope) // Manage scope appropriately for a singleton repository.
    }
}

// Note: Need to add JSON conversion helpers to GridElementDao or a separate converter.
// Also need updateSyncStatus method in GridElementDao.
