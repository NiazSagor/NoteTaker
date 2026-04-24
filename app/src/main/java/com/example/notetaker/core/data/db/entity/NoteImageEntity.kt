package com.example.notetaker.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.notetaker.core.domain.model.SyncStatus
import com.example.notetaker.core.domain.model.UploadStatus

@Entity(tableName = "note_images")
data class NoteImageEntity(
    @PrimaryKey val id: String = "",                // UUID
    val noteId: String = "",                        // FK to NoteEntity
    val workspaceId: String = "",
    val orderInNote: Int = 0,                      // display order within the note
    val localImageUri: String? = null,                // file:// URI before upload
    val remoteImageUrl: String? = null,               // Cloudinary HTTPS URL after upload
    val rotationDegrees: Float = 0f,           // persisted rotation from 3-finger gesture
    val uploadStatus: UploadStatus = UploadStatus.NONE,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val createdBy: String = "",

    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val localVersion: Int = 0,
    val remoteVersion: Int = 0,
    val deleted: Boolean = false,

    // DEBUG fields
    val lastSyncAttemptAt: Long? = null,
    val lastSyncError: String? = null,
    val rotationHistory: String? = null        // DEBUG: JSON array of last 5 rotation values
) {
    fun toFirestoreMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "noteId" to noteId,
            "workspaceId" to workspaceId,
            "orderInNote" to orderInNote,
            "localImageUri" to localImageUri,
            "remoteImageUrl" to remoteImageUrl,
            "rotationDegrees" to rotationDegrees,
            "uploadStatus" to uploadStatus.name,
            "createdAt" to createdAt,
            "updatedAt" to updatedAt,
            "createdBy" to createdBy,
            "remoteVersion" to remoteVersion,
            "deleted" to deleted
        )
    }
}
