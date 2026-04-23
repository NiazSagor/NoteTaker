package com.example.notetaker.core.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.example.notetaker.core.data.db.dao.NoteImageDao
import com.example.notetaker.core.data.db.entity.NoteImageEntity
import com.example.notetaker.core.domain.model.SyncStatus
import com.example.notetaker.core.domain.model.UploadStatus
import com.example.notetaker.core.network.cloudinary.CloudinaryUploadClient // Assuming this client exists or will be created
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
    private val cloudinaryUploadClient: CloudinaryUploadClient // Inject the Cloudinary client
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                val pendingImages = noteImageDao.getImagesToUpload() // Needs implementation in NoteImageDao

                if (pendingImages.isEmpty()) {
                    return@withContext Result.success() // Nothing to upload
                }

                val uploadResults = mutableListOf<suspend () -> Boolean>()

                pendingImages.forEach { image ->
                    uploadResults.add {
                        try {
                            // 1. Mark as UPLOADING
                            noteImageDao.updateUploadStatus(image.id, UploadStatus.UPLOADING)

                            // 2. Upload to Cloudinary
                            // This requires a CloudinaryUploadClient implementation
                            val remoteUrl = cloudinaryUploadClient.uploadImage(image.localImageUri ?: "") // Pass local URI

                            // 3. Update local entity with remote URL and status
                            val updatedImage = image.copy(
                                remoteImageUrl = remoteUrl,
                                uploadStatus = UploadStatus.DONE,
                                syncStatus = SyncStatus.PENDING // Mark for main sync worker to push metadata to Firestore
                            )
                            noteImageDao.upsert(updatedLocalNote) // Should be updatedImage

                            true // Indicate success for this image
                        } catch (e: Exception) {
                            noteImageDao.updateUploadStatus(image.id, UploadStatus.FAILED)
                            // Log error details here
                            false // Indicate failure for this image
                        }
                    }
                }

                val overallSuccess = uploadResults.map { async { it() } }.awaitAll().all { it }

                if (overallSuccess) {
                    Result.success()
                } else {
                    Result.retry() // Retry if any upload failed
                }

            } catch (e: Exception) {
                // Handle general errors during the upload process
                // Log.e("CloudinaryWorker", "Cloudinary upload failed", e)
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
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    OneTimeWorkRequest.MIN_BACKOFF_MILLIS,
                    OneTimeWorkRequest.MAX_BACKOFF_MILLIS
                )
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

// Placeholder DAO extensions - these need to be implemented in NoteImageDao
// suspend fun NoteImageDao.getImagesToUpload(): List<NoteImageEntity> = emptyList()
// suspend fun NoteImageDao.updateUploadStatus(id: String, status: UploadStatus) { /* ... */ }
