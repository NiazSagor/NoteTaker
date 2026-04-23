package com.example.notetaker.core.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.example.notetaker.core.data.db.dao.ConflictDao
import com.example.notetaker.core.data.db.dao.GridElementDao
import com.example.notetaker.core.data.db.dao.NoteDao
import com.example.notetaker.core.data.db.dao.NoteImageDao
import com.example.notetaker.core.data.db.entity.GridElementEntity
import com.example.notetaker.core.data.db.entity.NoteEntity
import com.example.notetaker.core.data.db.entity.NoteImageEntity
import com.example.notetaker.core.domain.model.SyncStatus
import com.example.notetaker.core.network.firebase.FirestoreSource
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                val workspaceId = "global_workspace" // Hardcoded for now

                // Fetch pending/errored local changes
                val pendingNotes = noteDao.getPendingOrErrorNotes()
                val pendingGridElements = gridElementDao.getPendingOrErrorGridElements()
                val pendingNoteImages = noteImageDao.getPendingOrErrorNoteImages()

                val allPendingItems = mutableListOf<suspend () -> Boolean>() // List of suspend functions returning success status

                // Process Notes
                if (pendingNotes.isNotEmpty()) {
                    allPendingItems.add {
                        pendingNotes.forEach { note ->
                            try {
                                // We assume that if a note is PENDING/ERROR, it does NOT have a TRUE_CONFLICT state
                                // that needs to be resolved BEFORE pushing. Conflict resolution happens on read path.
                                firestoreSource.upsertNote(workspaceId, note)
                                noteDao.updateSyncStatus(note.id, SyncStatus.SYNCED)
                            } catch (e: Exception) {
                                noteDao.updateSyncStatus(note.id, SyncStatus.ERROR)
                                // Log error details here
                                return@forEach false // Indicate failure for this note
                            }
                        }
                        true // Indicate success for this batch
                    }
                }

                // Process Grid Elements
                if (pendingGridElements.isNotEmpty()) {
                    allPendingItems.add {
                        pendingGridElements.forEach { element ->
                            try {
                                firestoreSource.upsertGridElement(workspaceId, element)
                                gridElementDao.updateSyncStatus(element.id, SyncStatus.SYNCED)
                            } catch (e: Exception) {
                                gridElementDao.updateSyncStatus(element.id, SyncStatus.ERROR)
                                // Log error
                                return@forEach false
                            }
                        }
                        true
                    }
                }

                // Process Note Images
                if (pendingNoteImages.isNotEmpty()) {
                    allPendingItems.add {
                        pendingNoteImages.forEach { image ->
                            try {
                                // NoteImage upload might be more complex (e.g., Cloudinary first)
                                // For now, assume direct Firestore upsert for metadata.
                                firestoreSource.upsertNoteImage(workspaceId, image.noteId, image)
                                noteImageDao.updateSyncStatus(image.id, SyncStatus.SYNCED)
                            } catch (e: Exception) {
                                noteImageDao.updateSyncStatus(image.id, SyncStatus.ERROR)
                                // Log error
                                return@forEach false
                            }
                        }
                        true
                    }
                }

                // Execute all pending operations concurrently
                val results = allPendingItems.map { async { it() } }.awaitAll()
                val overallSuccess = results.all { it }

                if (overallSuccess) {
                    Result.success() // All items processed without critical errors that require retry
                } else {
                    // If any item failed to sync and was marked ERROR, we might want to retry.
                    // For simplicity, let's retry if any operation reported false (failure).
                    // A more granular approach could check if there are still PENDING items.
                    Result.retry()
                }

            } catch (e: Exception) {
                // Handle general errors during the sync process
                // Log.e("SyncWorker", "Sync failed", e)
                Result.retry() // Retry on failure
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
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    OneTimeWorkRequest.MIN_BACKOFF_MILLIS,
                    OneTimeWorkRequest.MAX_BACKOFF_MILLIS
                )
                .addTag(SYNC_WORKER_TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                SYNC_WORKER_TAG,
                ExistingWorkPolicy.KEEP, // Keep the existing worker if it's already scheduled
                request
            )
        }
    }
}
