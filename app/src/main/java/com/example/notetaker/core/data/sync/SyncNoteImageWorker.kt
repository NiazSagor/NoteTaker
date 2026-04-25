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
import com.example.notetaker.core.data.db.dao.NoteImageDao
import com.example.notetaker.core.domain.model.SyncStatus
import com.example.notetaker.core.network.firebase.FirestoreSource
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncNoteImageWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val firestore: FirestoreSource,
    private val noteImageDao: NoteImageDao
) : CoroutineWorker(context, params) {
    private val TAG = "SyncNoteImageWorker"
    private val workspaceId = "global_workspace"

    override suspend fun doWork(): Result {
        val imageId = inputData.getString("imageId") ?: return Result.failure()

        val image = noteImageDao.getById(imageId) ?: return Result.success()

        return try {
            val imageToPush = image.copy(remoteVersion = image.remoteVersion + 1)
            firestore.upsertNoteImage(workspaceId, image.noteId, imageToPush.toDto())
            noteImageDao.upsert(
                imageToPush.copy(
                    syncStatus = SyncStatus.SYNCED,
                    localVersion = 0
                )
            )
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "doWork: Exception syncing note image $imageId", e)
            Result.retry()
        }
    }

    companion object {

        fun createWorkRequest(imageId: String): androidx.work.OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<SyncNoteImageWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setInputData(
                    androidx.work.workDataOf("imageId" to imageId)
                )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
        }
    }
}
