package com.example.notetaker.core.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
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
import com.example.notetaker.core.network.firebase.FirestoreSource
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

@HiltWorker
class SyncPendingWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val noteDao: NoteDao,
    private val gridElementDao: GridElementDao,
    private val noteImageDao: NoteImageDao,
    private val conflictDao: ConflictDao,
    private val firestoreSource: FirestoreSource,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : CoroutineWorker(appContext, workerParams) {

    private val TAG = "SyncPendingWorker"
    private val workspaceId = "global_workspace" // Hardcoded for now

    override suspend fun doWork(): Result {
        return withContext(ioDispatcher) {
            try {
                // Fetch all items marked as PENDING or ERROR from local DB
                val pendingNotes = noteDao.getPendingOrErrorNotes()
                val pendingGridElements = gridElementDao.getPendingOrErrorGridElements()
                val pendingNoteImages = noteImageDao.getPendingOrErrorNoteImages()

                awaitAll(
                    async { processNotes(pendingNotes) },
                    async { processGridElements(pendingGridElements) },
                    async { processNoteImages(pendingNoteImages) }
                )

                // If any of the async operations returned Result.retry(), the whole worker should retry.
                // For now, assume success unless an exception is thrown.
                Result.success()

            } catch (e: Exception) {
                Log.e(TAG, "SyncPendingWorker failed", e)
                Result.retry() // Retry the entire worker if any critical error occurs
            }
        }
    }

    private suspend fun processNotes(pendingNotes: List<NoteEntity>) {
        for (localNote in pendingNotes) {
            try {
                // 1. Fetch remote state for this note
                val remoteNoteDto = firestoreSource.getNote(workspaceId, localNote.id)

                if (remoteNoteDto != null) {
                    // Conflict detection logic
                    val conflictType = ConflictDetector.detect(
                        localVersion = localNote.localVersion,
                        remoteVersionAtLocal = localNote.remoteVersion,
                        incomingRemoteVersion = remoteNoteDto.remoteVersion
                    )

                    when (conflictType) {
                        ConflictType.STALE, ConflictType.NO_CHANGE -> {
                            // If remote is same or older, push local change
                            pushLocalNote(localNote)
                        }

                        ConflictType.REMOTE_ADVANCED -> {
                            // Remote is ahead, safe to apply remote if not deleted
                            if (remoteNoteDto.deleted) {
                                noteDao.deleteById(localNote.id)
                            } else {
                                noteDao.upsert(remoteNoteDto.toEntity(SyncStatus.SYNCED)) // Apply remote changes
                            }
                        }

                        ConflictType.LOCAL_ADVANCED -> {
                            // Local change is ahead, push it.
                            pushLocalNote(localNote)
                        }

                        ConflictType.CLEAN_FAST_FORWARD -> {
                            // Remote advanced one step, apply remote and keep local version for next sync
                            val remoteNote = remoteNoteDto.toEntity(SyncStatus.SYNCED)
                            noteDao.upsert(remoteNote.copy(localVersion = localNote.localVersion))
                        }

                        ConflictType.TRUE_CONFLICT -> {
                            // TRUE CONFLICT: Flag and save snapshots
                            val conflict = ConflictEntity(
                                noteId = localNote.id,
                                workspaceId = workspaceId,
                                localSnapshot = noteDao.convertNoteEntityToJson(localNote.toDomain()),
                                remoteSnapshot = noteDao.convertNoteEntityToJson(
                                    remoteNoteDto.toEntity(
                                        SyncStatus.SYNCED
                                    ).toDomain()
                                ), // Convert fetched DTO to domain for snapshot
                                localVersion = localNote.localVersion,
                                remoteVersion = remoteNoteDto.remoteVersion, // Use DTO's remote version here
                                expectedVersion = remoteNoteDto.remoteVersion, // Expected version based on remote
                                detectedAt = System.currentTimeMillis(),
                                isResolved = false
                            )
                            conflictDao.upsert(conflict)
                            noteDao.updateSyncStatus(localNote.id, SyncStatus.CONFLICT)
                        }
                    }
                } else {
                    // Local note doesn't exist, handle new remote notes (if not deleted)
                    remoteNoteDto?.deleted?.let {
                        if (!it) {
                            noteDao.upsert(remoteNoteDto.toEntity(SyncStatus.SYNCED))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing note ${localNote.id}", e)
                noteDao.updateSyncStatus(
                    localNote.id,
                    SyncStatus.ERROR
                ) // Mark as error to retry later
            }
        }
    }

    private suspend fun processGridElements(pendingGridElements: List<GridElementEntity>) {
        for (localElement in pendingGridElements) {
            try {
                // Fetch remote state
                val remoteElementDto = firestoreSource.getGridElement(workspaceId, localElement.id)

                if (remoteElementDto != null) {
                    // LAST WRITE WINS but ignore stale (Section 6.5)
                    if (remoteElementDto.remoteVersion > localElement.remoteVersion) {
                        val remoteElement = remoteElementDto.toEntity(SyncStatus.SYNCED)
                        if (remoteElement.deleted) {
                            gridElementDao.deleteById(remoteElement.id)
                        } else {
                            val updatedLocalElement = remoteElement.copy(
                                localVersion = localElement.localVersion, // Keep local version if it's ahead, but apply remote data
                                syncStatus = localElement.syncStatus // Preserve local sync status if not a conflict
                            )
                            gridElementDao.upsert(updatedLocalElement)
                        }
                    } else {
                        // Remote is not ahead, push local change
                        pushLocalGridElement(localElement)
                    }
                } else {
                    // Remote element doesn't exist, push local change
                    pushLocalGridElement(localElement)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing grid element ${localElement.id}", e)
                gridElementDao.updateSyncStatus(localElement.id, SyncStatus.ERROR)
            }
        }
    }

    private suspend fun processNoteImages(pendingNoteImages: List<NoteImageEntity>) {
//        for (localImage in pendingNoteImages) {
//            try {
//                // Fetch remote state for this image
//                val remoteImageDto = firestoreSource.getNoteImage(workspaceId, localImage.noteId, localImage.id)
//
//                if (remoteImageDto != null) {
//                    // LAST WRITE WINS but ignore stale
//                    if (remoteImageDto.remoteVersion > localImage.remoteVersion) {
//                        val remoteImage = remoteImageDto.toEntity(SyncStatus.SYNCED)
//                        if (remoteImage.deleted) {
//                            noteImageDao.deleteById(remoteImage.id)
//                        } else {
//                            val updatedLocalImage = remoteImage.copy(
//                                localVersion = localImage.localVersion,
//                                syncStatus = localImage.syncStatus
//                            )
//                            noteImageDao.upsert(updatedLocalImage)
//                        }
//                    } else {
//                        // Remote is not ahead, push local change
//                        pushLocalNoteImage(localImage)
//                    }
//                } else {
//                    // Remote image doesn't exist, push local change
//                    pushLocalNoteImage(localImage)
//                }
//            } catch (e: Exception) {
//                Log.e(TAG, "Error processing note image ${localImage.id}", e)
//                noteImageDao.updateSyncStatus(localImage.id, SyncStatus.ERROR)
//            }
//        }
    }

    // Helper to push local note changes
    private suspend fun pushLocalNote(note: NoteEntity) {
        try {
            val noteToPush = note.copy(remoteVersion = note.remoteVersion + 1)
            firestoreSource.upsertNote(workspaceId, noteToPush.toDto())
            noteDao.upsert(noteToPush.copy(syncStatus = SyncStatus.SYNCED, localVersion = 0))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to push note ${note.id} to remote", e)
            noteDao.updateSyncStatus(note.id, SyncStatus.ERROR) // Mark as error to retry
        }
    }

    // Helper to push local grid element changes
    private suspend fun pushLocalGridElement(element: GridElementEntity) {
        try {
            val elementToPush = element.copy(remoteVersion = element.remoteVersion + 1)
            firestoreSource.upsertGridElement(workspaceId, elementToPush.toDto())
            gridElementDao.upsert(
                elementToPush.copy(
                    syncStatus = SyncStatus.SYNCED,
                    localVersion = 0
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to push grid element ${element.id} to remote", e)
            gridElementDao.updateSyncStatus(element.id, SyncStatus.ERROR)
        }
    }

    // Helper to push local note image changes
    private suspend fun pushLocalNoteImage(image: NoteImageEntity) {
        try {
            val remoteVersionBeforePush = image.remoteVersion
            val imageDto = image.toDto() // Convert Entity to DTO for Firestore

            firestoreSource.upsertNoteImage(workspaceId, image.noteId, imageDto)

            noteImageDao.upsert(
                image.copy(
                    syncStatus = SyncStatus.SYNCED,
                    remoteVersion = remoteVersionBeforePush + 1,
                    localVersion = 0
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to push note image ${image.id} to remote", e)
            noteImageDao.updateSyncStatus(image.id, SyncStatus.ERROR)
        }
    }

    companion object {
        private const val SYNC_PENDING_WORKER_TAG = "sync_pending_worker"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<SyncPendingWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .addTag(SYNC_PENDING_WORKER_TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                SYNC_PENDING_WORKER_TAG,
                ExistingWorkPolicy.KEEP, // Keep existing worker if already scheduled
                request
            )
        }
    }
}
