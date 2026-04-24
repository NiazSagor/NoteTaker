package com.example.notetaker.core.data.sync

import com.example.notetaker.core.data.db.dao.ConflictDao
import com.example.notetaker.core.data.db.dao.GridElementDao
import com.example.notetaker.core.data.db.dao.NoteDao
import com.example.notetaker.core.data.db.dao.NoteImageDao
import com.example.notetaker.core.data.db.entity.ConflictEntity
import com.example.notetaker.core.data.db.entity.GridElementEntity
import com.example.notetaker.core.data.db.entity.NoteEntity
import com.example.notetaker.core.data.db.entity.NoteImageEntity
import com.example.notetaker.core.domain.conflict.ConflictDetector
import com.example.notetaker.core.domain.conflict.ConflictType
import com.example.notetaker.core.domain.di.IoDispatcher
import com.example.notetaker.core.domain.model.SyncStatus
import com.example.notetaker.core.domain.repository.AuthRepository
import com.example.notetaker.core.domain.repository.ConflictRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncProcessor @Inject constructor(
    private val noteDao: NoteDao,
    private val gridElementDao: GridElementDao,
    private val noteImageDao: NoteImageDao,
    private val conflictDao: ConflictDao,
    private val authRepository: AuthRepository,
    private val conflictRepository: ConflictRepository, // Inject ConflictRepository for saving conflicts
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val appScope: CoroutineScope // Application scope for background tasks
) {

    private val TAG = "SyncProcessor"
    private val workspaceId = "global_workspace" // Assuming a single global workspace

    suspend fun syncRemoteNote(remoteNote: NoteEntity) {
        withContext(ioDispatcher) {
            val localNote = noteDao.getNote(remoteNote.id)

            if (localNote != null) {
                // Local entity exists, perform conflict detection
                val conflictType = ConflictDetector.detect(
                    localVersion = localNote.localVersion,
                    remoteVersionAtLocal = localNote.remoteVersion,
                    incomingRemoteVersion = remoteNote.remoteVersion
                )

                when (conflictType) {
                    ConflictType.STALE, ConflictType.NO_CHANGE -> {
                        // Ignore stale or redundant updates
                    }
                    ConflictType.REMOTE_ADVANCED -> {
                        // Case 1: Safe to apply remote changes directly.
                        val updatedLocalNote = remoteNote.copy(
                            localVersion = 0, // Reset local changes as we have none against this remote
                            syncStatus = SyncStatus.SYNCED
                        )
                        noteDao.upsert(updatedLocalNote)
                    }
                    ConflictType.LOCAL_ADVANCED -> {
                        // Case 2: Local change is ahead. Do nothing.
                    }
                    ConflictType.CLEAN_FAST_FORWARD -> {
                        // Case 3: Remote advanced exactly one version. Apply and keep local draft.
                        val updatedLocalNote = remoteNote.copy(
                            localVersion = localNote.localVersion,
                            syncStatus = localNote.syncStatus
                        )
                        noteDao.upsert(updatedLocalNote)
                    }
                    ConflictType.TRUE_CONFLICT -> {
                        // Case 4: TRUE CONFLICT
                        val conflict = ConflictEntity(
                            id = UUID.randomUUID().toString(),
                            noteId = remoteNote.id,
                            workspaceId = workspaceId,
                            localSnapshot = noteDao.convertNoteEntityToJson(localNote),
                            remoteSnapshot = noteDao.convertNoteEntityToJson(remoteNote),
                            localVersion = localNote.localVersion,
                            remoteVersion = remoteNote.remoteVersion,
                            expectedVersion = remoteNote.remoteVersion,
                            detectedAt = System.currentTimeMillis(),
                            isResolved = false
                        )
                        conflictDao.upsert(conflict)
                        noteDao.updateSyncStatus(remoteNote.id, SyncStatus.CONFLICT)
                    }
                }
            } else {
                // Local note doesn't exist, handle new remote notes.
                if (!remoteNote.isDeleted) {
                    noteDao.upsert(remoteNote.copy(syncStatus = SyncStatus.SYNCED))
                }
            }
        }
    }

    suspend fun syncRemoteGridElement(remoteElement: GridElementEntity) {
        withContext(ioDispatcher) {
            val localElement = gridElementDao.getById(remoteElement.id)

            if (localElement != null) {
                // LAST WRITE WINS but ignore stale (Section 6.5)
                if (remoteElement.remoteVersion > localElement.remoteVersion) {
                    if (remoteElement.isDeleted) {
                        gridElementDao.deleteById(remoteElement.id)
                    } else {
                        val updatedLocalElement = remoteElement.copy(
                            localVersion = localElement.localVersion,
                            syncStatus = localElement.syncStatus
                        )
                        gridElementDao.upsert(updatedLocalElement)
                    }
                }
            } else {
                // Local element doesn't exist, handle new remote elements.
                if (!remoteElement.isDeleted) {
                    gridElementDao.upsert(remoteElement.copy(syncStatus = SyncStatus.SYNCED))
                }
            }
        }
    }

    suspend fun syncRemoteNoteImage(remoteImage: NoteImageEntity, noteId: String) {
        withContext(ioDispatcher) {
            val localImage = noteImageDao.getById(remoteImage.id)

            if (localImage != null) {
                // LAST WRITE WINS but ignore stale (Section 6.5)
                if (remoteImage.remoteVersion > localImage.remoteVersion) {
                    if (remoteImage.isDeleted) {
                        noteImageDao.deleteById(remoteImage.id)
                    } else {
                        val updatedLocalImage = remoteImage.copy(
                            localVersion = localImage.localVersion,
                            syncStatus = localImage.syncStatus
                        )
                        noteImageDao.upsert(updatedLocalImage)
                    }
                }
            } else {
                // Local image doesn't exist, handle new remote images.
                if (!remoteImage.isDeleted) {
                    noteImageDao.upsert(remoteImage.copy(syncStatus = SyncStatus.SYNCED))
                }
            }
        }
    }
}
