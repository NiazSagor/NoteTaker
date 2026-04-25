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
import com.example.notetaker.core.data.db.dao.GridElementDao
import com.example.notetaker.core.domain.model.SyncStatus
import com.example.notetaker.core.network.firebase.FirestoreSource
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncGridElementWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val firestore: FirestoreSource,
    private val gridElementDao: GridElementDao
) : CoroutineWorker(context, params) {
    private val TAG = "SyncGridElementWorker"
    private val workspaceId = "global_workspace"

    override suspend fun doWork(): Result {
        val elementId = inputData.getString("elementId") ?: return Result.failure()

        val element = gridElementDao.getById(elementId) ?: return Result.success()

        return try {
            val elementToPush = element.copy(remoteVersion = element.remoteVersion + 1)
            firestore.upsertGridElement(workspaceId, elementToPush)
            gridElementDao.upsert(
                elementToPush.copy(
                    syncStatus = SyncStatus.SYNCED,
                    localVersion = 0
                )
            )
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "doWork: Exception syncing grid element $elementId", e)
            Result.retry()
        }
    }

    companion object {
        fun enqueue(context: Context, elementId: String) {
            val request = OneTimeWorkRequestBuilder<SyncGridElementWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setInputData(
                    workDataOf("elementId" to elementId)
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
