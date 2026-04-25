package com.example.notetaker.core.network.firebase.model

import com.example.notetaker.core.data.db.entity.NoteEntity
import com.example.notetaker.core.domain.model.SyncStatus
import com.google.firebase.firestore.PropertyName

data class NoteDto(
    @get:PropertyName("id") @set:PropertyName("id") var id: String = "",
    @get:PropertyName("workspaceId") @set:PropertyName("workspaceId") var workspaceId: String = "",
    @get:PropertyName("title") @set:PropertyName("title") var title: String = "",
    @get:PropertyName("content") @set:PropertyName("content") var content: String = "",
    @get:PropertyName("createdAt") @set:PropertyName("createdAt") var createdAt: Long = 0L,
    @get:PropertyName("updatedAt") @set:PropertyName("updatedAt") var updatedAt: Long = 0L,
    @get:PropertyName("createdBy") @set:PropertyName("createdBy") var createdBy: String = "",
    @get:PropertyName("lastEditedBy") @set:PropertyName("lastEditedBy") var lastEditedBy: String = "",
    @get:PropertyName("remoteVersion") @set:PropertyName("remoteVersion") var remoteVersion: Int = 0,
    @get:PropertyName("deleted") @set:PropertyName("deleted") var deleted: Boolean = false
) {
    fun toEntity(syncStatus: SyncStatus = SyncStatus.SYNCED): NoteEntity {
        return NoteEntity(
            id = id,
            workspaceId = workspaceId,
            title = title,
            content = content,
            createdAt = createdAt,
            updatedAt = updatedAt,
            createdBy = createdBy,
            lastEditedBy = lastEditedBy,
            remoteVersion = remoteVersion,
            deleted = deleted,
            syncStatus = syncStatus,
            localVersion = 0
        )
    }
}
