package com.example.notetaker.core.data.sync

import android.content.Context
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages synchronization processes by enqueueing relevant WorkManager workers.
 * It acts as a facade for initiating background sync tasks for different data types,
 * ensuring a coordinated and efficient sync flow.
 */
@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun syncNote() {
        SyncPendingWorker.enqueue(context)
    }
    fun syncGridElement() {
        SyncPendingWorker.enqueue(context)
    }

    fun syncNoteImage(imageId: String) {
        // This chain handles asset upload first, then metadata sync.
        // It ensures that image assets are uploaded before their metadata is synced to Firestore.
        val uploadRequest = UploadWorker.createWorkRequest(imageId)
        val syncRequest = SyncNoteImageWorker.createWorkRequest(imageId)

        WorkManager.getInstance(context)
            .beginWith(uploadRequest)
            .then(syncRequest)
            .enqueue()
    }

    fun syncRotation(imageId: String) {
        val syncRequest = SyncNoteImageWorker.createWorkRequest(imageId)
        WorkManager.getInstance(context)
            .enqueue(syncRequest)
    }
}