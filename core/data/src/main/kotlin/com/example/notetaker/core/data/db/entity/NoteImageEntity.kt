package com.example.notetaker.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.notetaker.core.domain.model.SyncStatus
import com.example.notetaker.core.domain.model.UploadStatus

@Entity(tableName = "note_images")
data class NoteImageEntity(
    @PrimaryKey val id: String,                // UUID
    val noteId: String,                        // FK to NoteEntity
    val workspaceId: String,
    val orderInNote: Int,                      // display order within the note
    val localImageUri: String?,                // file:// URI before upload
    val remoteImageUrl: String?,               // Cloudinary HTTPS URL after upload
    val rotationDegrees: Float = 0f,           // persisted rotation from 3-finger gesture
    val uploadStatus: UploadStatus = UploadStatus.NONE,
    val createdAt: Long,
    val updatedAt: Long,
    val createdBy: String,

    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val localVersion: Int = 0,
    val remoteVersion: Int = 0,
    val isDeleted: Boolean = false,

    // DEBUG fields
    val lastSyncAttemptAt: Long? = null,
    val lastSyncError: String? = null,
    val rotationHistory: String? = null        // DEBUG: JSON array of last 5 rotation values
)
