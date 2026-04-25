package com.example.notetaker.core.network.firebase.model

import com.example.notetaker.core.data.db.entity.GridElementEntity
import com.example.notetaker.core.domain.model.GridElementType
import com.example.notetaker.core.domain.model.SyncStatus
import com.example.notetaker.core.domain.model.UploadStatus
import com.google.firebase.firestore.PropertyName

data class GridElementDto(
    @get:PropertyName("id") @set:PropertyName("id") var id: String = "",
    @get:PropertyName("workspaceId") @set:PropertyName("workspaceId") var workspaceId: String = "",
    @get:PropertyName("type") @set:PropertyName("type") var type: String = GridElementType.NOTE.name,
    @get:PropertyName("orderIndex") @set:PropertyName("orderIndex") var orderIndex: Double = 0.0,
    @get:PropertyName("createdAt") @set:PropertyName("createdAt") var createdAt: Long = 0L,
    @get:PropertyName("updatedAt") @set:PropertyName("updatedAt") var updatedAt: Long = 0L,
    @get:PropertyName("createdBy") @set:PropertyName("createdBy") var createdBy: String = "",
    @get:PropertyName("noteId") @set:PropertyName("noteId") var noteId: String? = null,
    @get:PropertyName("remoteImageUrl") @set:PropertyName("remoteImageUrl") var remoteImageUrl: String? = null,
    @get:PropertyName("remoteVersion") @set:PropertyName("remoteVersion") var remoteVersion: Int = 0,
    @get:PropertyName("deleted") @set:PropertyName("deleted") var deleted: Boolean = false
) {
    fun toEntity(syncStatus: SyncStatus = SyncStatus.SYNCED): GridElementEntity {
        return GridElementEntity(
            id = id,
            workspaceId = workspaceId,
            type = GridElementType.valueOf(type),
            orderIndex = orderIndex,
            createdAt = createdAt,
            updatedAt = updatedAt,
            createdBy = createdBy,
            noteId = noteId,
            remoteImageUrl = remoteImageUrl,
            syncStatus = syncStatus,
            localVersion = 0,
            remoteVersion = remoteVersion,
            deleted = deleted
        )
    }
}
