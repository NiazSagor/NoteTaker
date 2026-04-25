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
        SyncNoteWorker.enqueue(context, noteId)
    }

    fun syncGridElement(elementId: String) {
        SyncGridElementWorker.enqueue(context, elementId)
    }

    fun syncNoteImage(imageId: String) {
        val uploadRequest = ImageKitUploadWorker.createWorkRequest(imageId)
        val syncRequest = SyncNoteImageWorker.createWorkRequest(imageId)

        WorkManager.getInstance(context)
            .beginWith(uploadRequest)
            .then(syncRequest)
            .enqueue()
    }
}