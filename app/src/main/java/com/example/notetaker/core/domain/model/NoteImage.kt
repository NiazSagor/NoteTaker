package com.example.notetaker.core.domain.model

import com.example.notetaker.core.data.db.entity.NoteImageEntity
import kotlinx.serialization.Serializable

@Serializable
data class NoteImage(
    val id: String,
    val noteId: String,
    val workspaceId: String,
    val orderInNote: Int,
    val localImageUri: String?,
    val remoteImageUrl: String?,
    val rotationDegrees: Float,
    val uploadStatus: UploadStatus,
    val createdAt: Long,
    val updatedAt: Long,
    val createdBy: String,
    val syncStatus: SyncStatus,
    val remoteVersion: Int,
    val deleted: Boolean
) {
    fun toEntity(localVersion: Int = 0): NoteImageEntity {
        return NoteImageEntity(
            id = id,
            noteId = noteId,
            workspaceId = workspaceId,
            orderInNote = orderInNote,
            localImageUri = localImageUri,
            remoteImageUrl = remoteImageUrl,
            rotationDegrees = rotationDegrees,
            uploadStatus = uploadStatus,
            createdAt = createdAt,
            updatedAt = updatedAt,
            createdBy = createdBy,
            syncStatus = syncStatus,
            localVersion = localVersion,
            remoteVersion = remoteVersion,
            deleted = deleted
        )
    }
}
