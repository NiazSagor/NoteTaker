package com.example.notetaker.core.data.repository

import android.util.Log
import com.example.notetaker.core.data.db.dao.ConflictDao
import com.example.notetaker.core.data.db.dao.GridElementDao
import com.example.notetaker.core.data.db.dao.NoteImageDao
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
    private val conflictDao: ConflictDao,
    private val firestoreSource: FirestoreSource,
    private val syncProcessor: SyncProcessor,
    private val syncManager: SyncManager,
    private val noteImageDao: NoteImageDao,
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
            syncManager.syncGridElement(element.id)
        }
    }

    override suspend fun softDeleteGridElement(id: String) {
        withContext(ioDispatcher) {
            gridElementDao.softDelete(id)
            syncManager.syncGridElement(id)
        }
    }

    private fun observeRemoteGridElements() {
        appScope.launch {
            firestoreSource.observeGridElements(workspaceId)
                .flowOn(ioDispatcher)
                .onEach { remoteElements ->
                    remoteElements.forEach { remoteElement ->
                        Log.e(TAG, "observeRemoteGridElements: $remoteElement")
                        syncProcessor.syncRemoteGridElement(remoteElement)
                    }
                }
                .catch { e ->
                    Log.e(TAG, "Error observing remote grid elements", e)
                }
                .launchIn(appScope)
        }
    }

    private fun observeRemoteNoteImages(noteId: String) {
        appScope.launch {
            firestoreSource.observeNoteImages(workspaceId, noteId)
                .flowOn(ioDispatcher)
                .onEach { remoteImages ->
                    remoteImages.forEach { remoteImage ->
                        syncProcessor.syncRemoteNoteImage(remoteImage)
                    }
                }
                .catch { e ->
                    Log.e(TAG, "Error observing remote note images for note $noteId", e)
                }
                .launchIn(appScope)
        }
    }
}
