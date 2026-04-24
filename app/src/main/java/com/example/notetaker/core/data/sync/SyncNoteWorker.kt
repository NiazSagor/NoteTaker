package com.example.notetaker.core.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.notetaker.core.data.db.dao.NoteDao
import com.example.notetaker.core.domain.model.SyncStatus
import com.example.notetaker.core.network.firebase.FirestoreSource
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncNoteWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val firestore: FirestoreSource,
    private val noteDao: NoteDao
) : CoroutineWorker(context, params) {
    private val TAG = "SyncNoteWorker"
    private val workspaceId = "global_workspace"
    override suspend fun doWork(): Result {

        val noteId = inputData.getString("noteId") ?: return Result.failure()

        val note = noteDao.getNote(noteId) ?: return Result.success()

        return try {
            val noteToPush = note.copy(remoteVersion = note.remoteVersion + 1)
            firestore.upsertNote(workspaceId, noteToPush)
            noteDao.upsert(noteToPush.copy(syncStatus = SyncStatus.SYNCED, localVersion = 0))
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "doWork: Exception ", )
            Result.retry()
        }
    }

    companion object {

        fun enqueue(context: Context, noteId: String) {

            val request = OneTimeWorkRequestBuilder<SyncNoteWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setInputData(
                    workDataOf("noteId" to noteId)
                )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context)
                .enqueue(request)
        }
    }
}