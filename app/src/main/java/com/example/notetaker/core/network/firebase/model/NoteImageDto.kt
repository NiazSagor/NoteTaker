package com.example.notetaker.core.network.firebase.model

import com.example.notetaker.core.data.db.entity.NoteImageEntity
import com.example.notetaker.core.domain.model.SyncStatus
import com.example.notetaker.core.domain.model.UploadStatus
import com.google.firebase.firestore.PropertyName

data class NoteImageDto(
    @get:PropertyName("id") @set:PropertyName("id") var id: String = "",
    @get:PropertyName("noteId") @set:PropertyName("noteId") var noteId: String = "",
    @get:PropertyName("workspaceId") @set:PropertyName("workspaceId") var workspaceId: String = "",
    @get:PropertyName("orderInNote") @set:PropertyName("orderInNote") var orderInNote: Int = 0,
    @get:PropertyName("remoteImageUrl") @set:PropertyName("remoteImageUrl") var remoteImageUrl: String? = null,
    @get:PropertyName("rotationDegrees") @set:PropertyName("rotationDegrees") var rotationDegrees: Float = 0f,
    @get:PropertyName("createdAt") @set:PropertyName("createdAt") var createdAt: Long = 0L,
    @get:PropertyName("updatedAt") @set:PropertyName("updatedAt") var updatedAt: Long = 0L,
    @get:PropertyName("createdBy") @set:PropertyName("createdBy") var createdBy: String = "",
    @get:PropertyName("remoteVersion") @set:PropertyName("remoteVersion") var remoteVersion: Int = 0,
    @get:PropertyName("deleted") @set:PropertyName("deleted") var deleted: Boolean = false
) {
    fun toEntity(syncStatus: SyncStatus = SyncStatus.SYNCED): NoteImageEntity {
        return NoteImageEntity(
            id = id,
            noteId = noteId,
            workspaceId = workspaceId,
            orderInNote = orderInNote,
            remoteImageUrl = remoteImageUrl,
            rotationDegrees = rotationDegrees,
            createdAt = createdAt,
            updatedAt = updatedAt,
            createdBy = createdBy,
            syncStatus = syncStatus,
            localVersion = 0,
            remoteVersion = remoteVersion,
            deleted = deleted
        )
    }
}
