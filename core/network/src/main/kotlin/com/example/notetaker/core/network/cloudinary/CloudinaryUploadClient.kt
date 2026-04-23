package com.example.notetaker.core.network.cloudinary

import android.util.Log
import com.cloudinary.android.MediaManager
import com.cloudinary.android.media.UploadResult
import com.cloudinary.android.payload.ByteArrayPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudinaryUploadClientImpl @Inject constructor(
    private val mediaManager: MediaManager // Assuming MediaManager is configured elsewhere and injected
) : CloudinaryUploadClient {
    override suspend fun uploadImage(localUri: String): String = withContext(Dispatchers.IO) {
        try {
            // Use the MediaManager to upload the image
            // The localUri is expected to be a file:// URI.
            // For byte array or other sources, adjust payload accordingly.
            val result = mediaManager.upload(localUri)
                .payload(ByteArrayPayload(localUri)) // Adjust payload based on actual URI type
                .unsigned("YOUR_UNSIGNED_UPLOAD_PRESET") // Use your actual unsigned upload preset
                .await() // Use await() for coroutine compatibility

            // The result should contain the secure URL
            val secureUrl = result.get("secure_url")?.toString()
            if (secureUrl != null) {
                Log.d("CloudinaryUpload", "Image uploaded successfully: $secureUrl")
                secureUrl
            } else {
                Log.e("CloudinaryUpload", "Upload succeeded but secure_url is missing in result.")
                throw IllegalStateException("Cloudinary upload missing secure_url")
            }
        } catch (e: Exception) {
            Log.e("CloudinaryUpload", "Error uploading image from $localUri", e)
            throw e // Re-throw to be caught by the caller (Worker)
        }
    }
}

// Note:
// 1. Cloudinary SDK setup: MediaManager needs to be initialized, likely in your Application class,
//    with your cloud_name and an unsigned upload_preset.
// 2. Error handling: More specific error handling and logging should be implemented.
// 3. Payload type: The payload type might need adjustment based on how localUri is handled.
// 4. Unsigned Preset: Replace "YOUR_UNSIGNED_UPLOAD_PRESET" with your actual preset.
