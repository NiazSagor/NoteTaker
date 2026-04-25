package com.example.notetaker.core.data.sync

import android.content.Context
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun syncNote(noteId: String) {
        SyncPendingWorker.enqueue(context)
    }
    fun syncGridElement(elementId: String) {
        SyncPendingWorker.enqueue(context)
    }

    fun syncNoteImage(imageId: String) {
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