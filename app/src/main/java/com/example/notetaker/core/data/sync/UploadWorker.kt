package com.example.notetaker.core.data.sync

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkerParameters
import com.example.notetaker.core.data.db.dao.NoteImageDao
import com.example.notetaker.core.data.db.entity.NoteImageEntity
import com.example.notetaker.core.domain.model.SyncStatus
import com.example.notetaker.core.domain.model.UploadStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

/**
 * A [CoroutineWorker] responsible for uploading note images to Supabase storage.
 *
 * It handles both single-image uploads (via specific imageId) and bulk uploads of all
 * pending images. Upon successful upload, it updates the local database with the remote URL.
 */
@HiltWorker
class UploadWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val noteImageDao: NoteImageDao,
    private val supabase: SupabaseClient,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                val targetImageId = inputData.getString("imageId")
                val imagesToUpload = if (targetImageId != null) {
                    val image = noteImageDao.getById(targetImageId)
                    if (image != null && image.uploadStatus != UploadStatus.DONE) listOf(image) else emptyList()
                } else {
                    noteImageDao.getImagesToUpload()
                }

                if (imagesToUpload.isEmpty()) {
                    return@withContext Result.success()
                }

                val uploadResults = imagesToUpload.map { image ->
                    async {
                        try {
                            noteImageDao.updateUploadStatus(image.id, UploadStatus.UPLOADING)
                            val remoteUrl = uploadNoteImage(image)
                            val updatedImage = image.copy(
                                remoteImageUrl = remoteUrl,
                                uploadStatus = UploadStatus.DONE,
                                syncStatus = SyncStatus.PENDING
                            )
                            noteImageDao.upsert(updatedImage)

                            true
                        } catch (e: Exception) {
                            Log.e("ImageKitWorker", "Failed to upload image ${image.id}", e)
                            noteImageDao.updateUploadStatus(image.id, UploadStatus.FAILED)
                            false
                        }
                    }
                }

                val results = uploadResults.awaitAll()
                val overallSuccess = results.all { it }

                if (overallSuccess) {
                    Result.success()
                } else {
                    Result.retry()
                }

            } catch (e: Exception) {
                Log.e("ImageKitWorker", "General error during ImageKit upload", e)
                Result.retry()
            }
        }
    }

    private suspend fun uploadNoteImage(
        image: NoteImageEntity
    ): String {
        return withContext(Dispatchers.IO) {
            val imageUri = Uri.parse(image.localImageUri)
            val inputStream =
                appContext.contentResolver.openInputStream(imageUri)
                    ?: throw Exception("Note image URI is invalid")
            val byteArray = inputStream.readBytes()
            val fileName = "public/${image.id}.jpg"
            supabase.storage.from("notes").upload(path = fileName, data = byteArray)
            supabase.storage.from("notes").publicUrl(fileName)
        }
    }

    companion object {
        private const val UPLOAD_WORKER_TAG = "imagekit_upload_worker"

        fun createWorkRequest(imageId: String): androidx.work.OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<UploadWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setInputData(androidx.work.workDataOf("imageId" to imageId))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .addTag(UPLOAD_WORKER_TAG)
                .build()
        }
    }
}
