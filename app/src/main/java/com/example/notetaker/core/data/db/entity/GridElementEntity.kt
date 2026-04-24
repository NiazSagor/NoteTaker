package com.example.notetaker.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.notetaker.core.domain.model.GridElementType
import com.example.notetaker.core.domain.model.SyncStatus
import com.example.notetaker.core.domain.model.UploadStatus

@Entity(tableName = "grid_elements")
data class GridElementEntity(
    @PrimaryKey val id: String = "",                // UUID
    val workspaceId: String = "",                   // FK to WorkspaceEntity
    val type: GridElementType = GridElementType.NOTE,                 // NOTE or STANDALONE_IMAGE
    val orderIndex: Double = 0.0,                    // fractional index for ordering
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val createdBy: String = "",                     // Firebase UID

    // Only populated when type == NOTE
    val noteId: String? = null,

    // Only populated when type == STANDALONE_IMAGE
    val localImageUri: String? = null,         // file:// URI before upload
    val remoteImageUrl: String? = null,        // Cloudinary HTTPS URL after upload
    val uploadStatus: UploadStatus = UploadStatus.NONE,

    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val localVersion: Int = 0,
    val remoteVersion: Int = 0,
    val deleted: Boolean = false,

    // DEBUG fields
    val lastSyncAttemptAt: Long? = null,
    val lastSyncError: String? = null,
    val debugTag: String? = null               // DEBUG: human label for logging
) {
    fun toFirestoreMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "workspaceId" to workspaceId,
            "type" to type.name,
            "orderIndex" to orderIndex,
            "createdAt" to createdAt,
            "updatedAt" to updatedAt,
            "createdBy" to createdBy,
            "noteId" to noteId,
            "localImageUri" to localImageUri,
            "remoteImageUrl" to remoteImageUrl,
            "uploadStatus" to uploadStatus.name,
            "remoteVersion" to remoteVersion,
            "deleted" to deleted
        )
    }
}
