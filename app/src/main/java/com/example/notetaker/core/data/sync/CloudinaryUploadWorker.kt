package com.example.notetaker.core.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.notetaker.core.data.db.dao.NoteImageDao
import com.example.notetaker.core.domain.model.SyncStatus
import com.example.notetaker.core.domain.model.UploadStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

@HiltWorker
class CloudinaryUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val noteImageDao: NoteImageDao,
//    private val cloudinaryUploadClient: CloudinaryUploadClient
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                val imagesToUpload = noteImageDao.getImagesToUpload()

                if (imagesToUpload.isEmpty()) {
                    return@withContext Result.success() // Nothing to upload
                }

                // Use async for concurrent uploads
                val uploadResults = imagesToUpload.map { image ->
                    async {
                        try {
                            // 1. Mark as UPLOADING
                            noteImageDao.updateUploadStatus(image.id, UploadStatus.UPLOADING)

                            // 2. Upload to Cloudinary
                            // This calls the injected client's uploadImage method.
                            val remoteUrl = "cloudinaryUploadClient.uploadImage(image.localImageUri ?: )"

                            // 3. Update local entity with remote URL and status
                            val updatedImage = image.copy(
                                remoteImageUrl = remoteUrl,
                                uploadStatus = UploadStatus.DONE,
                                syncStatus = SyncStatus.PENDING // Mark for main sync worker to push metadata to Firestore
                            )
                            noteImageDao.upsert(updatedImage) // Correctly use updatedImage

                            true // Indicate success for this image
                        } catch (e: Exception) {
                            Log.e("CloudinaryWorker", "Failed to upload image ${image.id} from ${image.localImageUri}", e)
                            noteImageDao.updateUploadStatus(image.id, UploadStatus.FAILED)
                            false // Indicate failure for this image
                        }
                    }
                }

                val results = uploadResults.awaitAll()
                val overallSuccess = results.all { it }

                if (overallSuccess) {
                    Result.success()
                } else {
                    // If any upload failed, retry the entire worker later
                    Result.retry()
                }

            } catch (e: Exception) {
                // Handle general errors during the upload process
                Log.e("CloudinaryWorker", "General error during Cloudinary upload", e)
                Result.retry() // Retry on failure
            }
        }
    }

    companion object {
        private const val UPLOAD_WORKER_TAG = "cloudinary_upload_worker"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<CloudinaryUploadWorker>()
                .setConstraints(constraints)
//                .setBackoffCriteria(
//                    BackoffPolicy.EXPONENTIAL,
//                    OneTimeWorkRequest.MIN_BACKOFF_MILLIS,
//                    OneTimeWorkRequest.MAX_BACKOFF_MILLIS
//                )
                .addTag(UPLOAD_WORKER_TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UPLOAD_WORKER_TAG,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}