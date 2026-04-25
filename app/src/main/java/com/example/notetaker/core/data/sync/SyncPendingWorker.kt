package com.example.notetaker.core.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.notetaker.core.data.db.dao.ConflictDao
import com.example.notetaker.core.data.db.dao.GridElementDao
import com.example.notetaker.core.data.db.dao.NoteDao
import com.example.notetaker.core.data.db.dao.NoteImageDao
import com.example.notetaker.core.domain.model.SyncStatus
import com.example.notetaker.core.domain.model.UploadStatus
import com.example.notetaker.core.network.firebase.FirestoreSource
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class SyncPendingWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val noteDao: NoteDao,
    private val gridElementDao: GridElementDao,
    private val noteImageDao: NoteImageDao,
    private val conflictDao: ConflictDao, // Potentially needed for conflict resolution updates
    private val firestoreSource: FirestoreSource
) : CoroutineWorker(appContext, workerParams) {

    private val TAG = "SyncPendingWorker"
    private val workspaceId = "global_workspace" // Hardcoded for now

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            var overallSuccess = true // Assume success initially

            try {
                // Fetch pending/errored local changes
                val pendingNotes = noteDao.getPendingOrErrorNotes()
                val pendingGridElements = gridElementDao.getPendingOrErrorGridElements()
                val pendingNoteImages = noteImageDao.getPendingOrErrorNoteImages()

                // --- Process Notes ---
                if (pendingNotes.isNotEmpty()) {
                    pendingNotes.forEach { note ->
                        try {
                            // Push local note changes to Firestore
                            val noteToPush = note.copy(remoteVersion = note.remoteVersion + 1)
                            firestoreSource.upsertNote(workspaceId, noteToPush)
                            noteDao.upsert(noteToPush.copy(syncStatus = SyncStatus.SYNCED, localVersion = 0))
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to sync note ${note.id}: ${e.message}", e)
                            noteDao.updateSyncStatus(note.id, SyncStatus.ERROR)
                            overallSuccess = false // Mark as failed if any note sync fails
                        }
                    }
                }

                // --- Process Grid Elements ---
                if (pendingGridElements.isNotEmpty()) {
                    pendingGridElements.forEach { element ->
                        try {
                            val elementToPush = element.copy(remoteVersion = element.remoteVersion + 1)
                            firestoreSource.upsertGridElement(workspaceId, elementToPush)
                            gridElementDao.upsert(
                                elementToPush.copy(
                                    syncStatus = SyncStatus.SYNCED,
                                    localVersion = 0
                                )
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to sync grid element ${element.id}: ${e.message}", e)
                            gridElementDao.updateSyncStatus(element.id, SyncStatus.ERROR)
                            overallSuccess = false
                        }
                    }
                }

                // --- todo Process Note Images ---
//                if (pendingNoteImages.isNotEmpty()) {
//                    pendingNoteImages.forEach { image ->
//                        try {
//                            // This syncs the metadata (like remoteImageUrl, orderInNote) to Firestore.
//                            // The actual image file upload is handled by CloudinaryUploadWorker.
//                            // We assume CloudinaryUploadWorker has already run and set remoteImageUrl and syncStatus to PENDING.
//                            firestoreSource.upsertNoteImage(workspaceId, image.noteId, image)
//                            noteImageDao.updateSyncStatus(image.id, SyncStatus.SYNCED)
//                        } catch (e: Exception) {
//                            Log.e(TAG, "Failed to sync note image ${image.id}: ${e.message}", e)
//                            noteImageDao.updateUploadStatus(
//                                image.id,
//                                UploadStatus.FAILED
//                            ) // Also mark upload as failed if metadata sync fails
//                            noteImageDao.updateSyncStatus(image.id, SyncStatus.ERROR)
//                            overallSuccess = false
//                        }
//                    }
//                }

                // Determine final result
                if (overallSuccess) {
                    Result.success()
                } else {
                    // If any item failed, retry the entire worker.
                    // A more sophisticated approach could check if there are still PENDING items.
                    Result.retry()
                }

            } catch (e: Exception) {
                // Handle unexpected errors during the sync process
                Log.e(TAG, "Unexpected error during sync process", e)
                Result.retry() // Retry on general failure
            }
        }
    }

    companion object {
        private const val SYNC_WORKER_TAG = "sync_pending_worker"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<SyncPendingWorker>()
                .setConstraints(constraints)
//                .setBackoffCriteria(
//                    BackoffPolicy.EXPONENTIAL,
//                    OneTimeWorkRequest.MIN_BACKOFF_MILLIS,
//                    OneTimeWorkRequest.MAX_BACKOFF_MILLIS
//                )
                .addTag(SYNC_WORKER_TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                SYNC_WORKER_TAG,
                ExistingWorkPolicy.KEEP, // Keep existing work if it's already scheduled
                request
            )
        }
    }
}
