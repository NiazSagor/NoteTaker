package com.example.notetaker.core.network.imagekit

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageKitUploadClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
) {
    // TODO: move to secrets
    private val privateKey = "private_YKm5h9m7O1gt4liBc3vx+qQh6tU="

    suspend fun uploadImage(uriString: String, fileName: String): String =
        withContext(Dispatchers.IO) {
            val uri = Uri.parse(uriString)
            val file = getFileFromUri(uri) ?: throw Exception("Could not get file from URI")

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", fileName, file.asRequestBody("image/*".toMediaType()))
                .addFormDataPart("fileName", fileName)
                .addFormDataPart("useUniqueFileName", "true")
                .build()

            val request = Request.Builder()
                .url("https://upload.imagekit.io/api/v1/files/upload")
                .addHeader(
                    "Authorization",
                    Credentials.basic(privateKey, "")
                )
                .post(requestBody)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("Upload failed: ${response.code} ${response.message}")
                }
                val responseBody = response.body?.string() ?: throw Exception("Empty response body")
                val jsonObject = JSONObject(responseBody)
                jsonObject.getString("url")
            }
        }

    private fun getFileFromUri(uri: Uri): File? {
        // If it's a file URI, return the file directly
        if (uri.scheme == "file") {
            return uri.path?.let { File(it) }
        }

        // Otherwise, copy it to a temp file (like we did in FileUtils but here locally for upload)
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val tempFile = File(context.cacheDir, "upload_temp_${System.currentTimeMillis()}.jpg")
            FileOutputStream(tempFile).use { output ->
                inputStream.copyTo(output)
            }
            tempFile
        } catch (e: Exception) {
            null
        }
    }
}
