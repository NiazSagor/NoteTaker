package com.example.notetaker.core.domain.model

import com.example.notetaker.core.data.db.entity.GridElementEntity

data class GridElement(
    val id: String,
    val workspaceId: String,
    val type: GridElementType,
    val orderIndex: Double,
    val createdAt: Long,
    val updatedAt: Long,
    val createdBy: String,
    val noteId: String?,
    val localImageUri: String?,
    val remoteImageUrl: String?,
    val uploadStatus: UploadStatus,
    val syncStatus: SyncStatus,
    val remoteVersion: Int,
    val deleted: Boolean
) {
    fun toEntity(localVersion: Int = 0): GridElementEntity {
        return GridElementEntity(
            id = id,
            workspaceId = workspaceId,
            type = type,
            orderIndex = orderIndex,
            createdAt = createdAt,
            updatedAt = updatedAt,
            createdBy = createdBy,
            noteId = noteId,
            localImageUri = localImageUri,
            remoteImageUrl = remoteImageUrl,
            uploadStatus = uploadStatus,
            syncStatus = syncStatus,
            localVersion = localVersion,
            remoteVersion = remoteVersion,
            deleted = deleted
        )
    }
}
