package com.example.notetaker.core.network.cloudinary

import android.net.Uri
import android.util.Log
import com.cloudinary.android.MediaManager
import com.cloudinary.android.payload.FilePayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class CloudinaryUploadClientImpl @Inject constructor(
    private val mediaManager: MediaManager
) : CloudinaryUploadClient {

    private val TAG = "CloudinaryUploadClient"

    override suspend fun uploadImage(localUriString: String): String = withContext(Dispatchers.IO) {
        val localUri = Uri.parse(localUriString) // Ensure it's a Uri object if needed

        suspendCancellableCoroutine { continuation ->
            mediaManager.upload(localUri)
                .unsigned("YOUR_UNSIGNED_UPLOAD_PRESET") // **IMPORTANT**: Replace with your actual unsigned upload preset from BuildConfig/local.properties
                .payload(FilePayload(localUri.toString())) // Use the localUri string to specify the file path
                .callback(object : ObjectCallback<Any> {
                    override fun onSuccess(result: Any?) {
                        try {
                            @Suppress("UNCHECKED_CAST")
                            val uploadResultMap = result as? Map<String, Any>
                            val secureUrl = uploadResultMap?.get("secure_url")?.toString()

                            if (secureUrl != null) {
                                Log.d(TAG, "Image uploaded successfully: $secureUrl")
                                continuation.resume(secureUrl)
                            } else {
                                Log.e(TAG, "Cloudinary upload succeeded but secure_url is missing.")
                                continuation.resumeWithException(IllegalStateException("Cloudinary upload missing secure_url"))
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing successful upload result: ${e.message}", e)
                            continuation.resumeWithException(e)
                        }
                    }

                    override fun onError(error: ErrorInfo) {
                        Log.e(TAG, "Cloudinary upload error: ${error.errorMessage} (Code: ${error.code})", Exception(error.errorMessage))
                        continuation.resumeWithException(Exception("Cloudinary upload failed: ${error.errorMessage} (Code: ${error.code})"))
                    }

                    override fun onProgress(requestId: String?, bytesUploaded: Long, totalBytes: Long) {
                        // Optional: Implement progress reporting if needed
                        // Log.d(TAG, "Upload progress for $requestId: $bytesUploaded / $totalBytes")
                    }

                    override fun onReschedule(requestId: String?) {
                        // Optional: Handle reschedule if applicable
                    }
                })
                .await() // This is a custom extension for CoroutineWorker, should handle callback completion
        }
    }
}
