package com.example.notetaker.core.data.repository

import com.example.notetaker.core.data.db.dao.ConflictDao
import com.example.notetaker.core.data.db.dao.GridElementDao
import com.example.notetaker.core.data.db.entity.GridElementEntity
import com.example.notetaker.core.domain.di.IoDispatcher
import com.example.notetaker.core.domain.repository.GridElementRepository
import com.example.notetaker.core.network.firebase.FirestoreSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
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
    private val TAG = "GridElementRepoImpl"

    init {
        observeRemoteGridElements()
    }

    override fun observeGridElements(workspaceId: String): Flow<List<GridElementEntity>> {
        return gridElementDao.observeGridElements(workspaceId)
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
//        appScope.launch {
//            firestoreSource.observeGridElements(workspaceId)
//                .flowOn(ioDispatcher)
//                .onEach { remoteElements ->
//                    remoteElements.forEach { remoteElement ->
//                        val localElement = gridElementDao.getById(remoteElement.id)
//
//                        if (localElement != null) {
//                            // Local entity exists, perform conflict detection
//                            val conflictType = ConflictDetector.detect(
//                                localVersion = localElement.localVersion,
//                                remoteVersionAtLocal = localElement.remoteVersion,
//                                incomingRemoteVersion = remoteElement.remoteVersion
//                            )
//
//                            when (conflictType) {
//                                ConflictType.REMOTE_ADVANCED -> {
//                                    // Case 1: Safe to apply remote changes directly.
//                                    val updatedLocalElement = remoteElement.copy(
//                                        localVersion = localElement.localVersion,
//                                        syncStatus = localElement.syncStatus
//                                    )
//                                    gridElementDao.upsert(updatedLocalElement)
//                                }
//                                ConflictType.LOCAL_ADVANCED -> {
//                                    // Case 2: Local change is ahead. Do nothing.
//                                }
//                                ConflictType.CLEAN_FAST_FORWARD -> {
//                                    // Case 3: Remote advanced exactly one version. Safe to apply.
//                                    val updatedLocalElement = remoteElement.copy(
//                                        localVersion = localElement.localVersion,
//                                        syncStatus = localElement.syncStatus
//                                    )
//                                    gridElementDao.upsert(updatedLocalElement)
//                                }
//                                ConflictType.TRUE_CONFLICT -> {
//                                    // Case 4: Both local and remote diverged.
//                                    val conflict = ConflictEntity(
//                                        id = UUID.randomUUID().toString(),
//                                        noteId = remoteElement.noteId ?: "", // GridElement might not have noteId directly, needs mapping
//                                        workspaceId = workspaceId,
//                                        localSnapshot = gridElementDao.convertGridElementEntityToJson(localElement),
//                                        remoteSnapshot = gridElementDao.convertGridElementEntityToJson(remoteElement),
//                                        localVersion = localElement.localVersion,
//                                        remoteVersion = remoteElement.remoteVersion,
//                                        detectedAt = System.currentTimeMillis(),
//                                        isResolved = false
//                                    )
//                                    conflictDao.upsert(conflict)
//                                    gridElementDao.updateSyncStatus(remoteElement.id, SyncStatus.CONFLICT)
//                                }
//                            }
//                        } else {
//                            // Local element doesn't exist, but remote does.
//                            if (!remoteElement.isDeleted) {
//                                gridElementDao.upsert(remoteElement.copy(syncStatus = SyncStatus.SYNCED))
//                            } else {
//                                // Remote element is deleted, ensure local reflects this.
//                                //gridElementDao.deleteById(remoteElement.id) // Assuming deleteById method exists in GridElementDao
//                            }
//                        }
//                    }
//                }
//            }
//            .catch { e ->
//                Log.e(TAG, "Error observing remote grid elements", e)
//            }
//            .launchIn(appScope) // Use the injected application scope
    }
}


// Note: The following DAO methods are assumed to exist and need to be implemented:
// - deleteById(id: String) in GridElementDao
// - convertGridElementEntityToJson(element: GridElementEntity): String (already implemented in DAO)
// - The primary task here is ensuring the logic for handling conflict detection and updates is correctly placed.
