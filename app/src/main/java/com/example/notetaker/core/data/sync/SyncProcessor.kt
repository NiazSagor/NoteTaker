package com.example.notetaker.core.data.sync

import android.util.Log
import com.example.notetaker.core.data.db.dao.ConflictDao
import com.example.notetaker.core.data.db.dao.GridElementDao
import com.example.notetaker.core.data.db.dao.NoteDao
import com.example.notetaker.core.data.db.dao.NoteImageDao
import com.example.notetaker.core.data.db.entity.ConflictEntity
import com.example.notetaker.core.data.db.entity.GridElementEntity
import com.example.notetaker.core.data.db.entity.NoteEntity
import com.example.notetaker.core.data.db.entity.NoteImageEntity
import com.example.notetaker.core.domain.model.SyncStatus
import com.example.notetaker.core.domain.model.UploadStatus
import com.example.notetaker.core.domain.repository.AuthRepository
import com.example.notetaker.core.domain.repository.ConflictRepository
import com.example.notetaker.core.network.firebase.FirestoreSource
import com.example.notetaker.core.network.firebase.model.GridElementDto
import com.example.notetaker.core.network.firebase.model.NoteDto
import com.example.notetaker.core.network.firebase.model.NoteImageDto
import com.example.notetaker.core.domain.conflict.ConflictDetector
import com.example.notetaker.core.domain.conflict.ConflictType
import com.example.notetaker.core.domain.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Processor responsible for synchronizing remote data with the local database.
 *
 * It handles incoming DTOs from Firestore, performs conflict detection based on
 * versioning metadata (localVersion and remoteVersion), and updates the local DAOs
 * accordingly. It manages specific sync logic for Notes, GridElements, and NoteImages,
 * ensuring that local modifications and remote updates are reconciled according to
 * defined conflict resolution strategies.
 */
@Singleton
class SyncProcessor @Inject constructor(
    private val noteDao: NoteDao,
    private val gridElementDao: GridElementDao,
    private val noteImageDao: NoteImageDao,
    private val conflictDao: ConflictDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val workspaceId = "global_workspace"

    suspend fun syncRemoteNote(remoteNoteDto: NoteDto) {
        withContext(ioDispatcher) {
            val localNote = noteDao.getNote(remoteNoteDto.id)

            if (localNote != null) {
                val isDeleted = remoteNoteDto.deleted

                if (isDeleted) {
                    noteDao.deleteById(remoteNoteDto.id)
                    return@withContext
                }

                val remoteNote = remoteNoteDto.toEntity(SyncStatus.SYNCED)

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
                    // TODO: move convertNoteEntityToJson from dao 
                    ConflictType.TRUE_CONFLICT -> {
                        // Case 4: TRUE CONFLICT
                        val conflict = ConflictEntity(
                            noteId = remoteNote.id,
                            workspaceId = workspaceId,
                            localSnapshot = noteDao.convertNoteEntityToJson(localNote.toDomain()),
                            remoteSnapshot = noteDao.convertNoteEntityToJson(remoteNote.toDomain()),
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
                if (!remoteNoteDto.deleted) {
                    noteDao.upsert(remoteNoteDto.toEntity(SyncStatus.SYNCED))
                }
            }
        }
    }

    suspend fun syncRemoteGridElement(remoteElementDto: GridElementDto) {
        withContext(ioDispatcher) {
            val localElement = gridElementDao.getById(remoteElementDto.id)

            if (localElement != null) {
                val isDeleted = remoteElementDto.deleted
                if (isDeleted) {
                    gridElementDao.deleteById(remoteElementDto.id)
                    return@withContext
                }
                // LAST WRITE WINS but ignore stale (Section 6.5)
                if (remoteElementDto.remoteVersion > localElement.remoteVersion) {
                    val remoteElement = remoteElementDto.toEntity(SyncStatus.SYNCED)
                    if (remoteElement.deleted) {
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
                if (!remoteElementDto.deleted) {
                    gridElementDao.upsert(remoteElementDto.toEntity(SyncStatus.SYNCED))
                }
            }
        }
    }

    suspend fun syncRemoteNoteImage(remoteImageDto: NoteImageDto) {
        withContext(ioDispatcher) {
            val localImage = noteImageDao.getById(remoteImageDto.id)

            if (localImage != null) {
                // LAST WRITE WINS but ignore stale (Section 6.5)
                if (remoteImageDto.remoteVersion > localImage.remoteVersion) {
                    if (remoteImageDto.deleted) {
                        noteImageDao.deleteById(remoteImageDto.id)
                    } else {
                        val updatedLocalImage = remoteImageDto.toEntity(
                            syncStatus = localImage.syncStatus // Keep local sync status
                        )
                        noteImageDao.upsert(updatedLocalImage)
                    }
                }
            } else {
                // Local image doesn't exist, handle new remote images.
                if (!remoteImageDto.deleted) {
                    noteImageDao.upsert(remoteImageDto.toEntity(SyncStatus.SYNCED))
                }
            }
        }
    }
}
