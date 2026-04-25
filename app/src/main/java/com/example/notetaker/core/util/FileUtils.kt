package com.example.notetaker.core.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

object FileUtils {
    /**
     * Copies a file from a [Uri] to the app's internal storage.
     * @return The [Uri] of the copied file, or null if it fails.
     */
    fun copyUriToInternalStorage(context: Context, uri: Uri, folderName: String): Uri? {
        val contentResolver = context.contentResolver
        val fileName = "IMG_${UUID.randomUUID()}.jpg" // Simplified extension
        val folder = File(context.filesDir, folderName)
        if (!folder.exists()) {
            folder.mkdirs()
        }
        val targetFile = File(folder, fileName)

        return try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val outputStream = FileOutputStream(targetFile)
            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            Uri.fromFile(targetFile)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
