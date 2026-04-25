package com.example.notetaker.core.network.firebase.model

import com.example.notetaker.core.data.db.entity.WorkspaceEntity
import com.example.notetaker.core.domain.model.SyncStatus
import com.google.firebase.firestore.PropertyName

data class WorkspaceDto(
    @get:PropertyName("id") @set:PropertyName("id") var id: String = "",
    @get:PropertyName("name") @set:PropertyName("name") var name: String = "",
    @get:PropertyName("createdAt") @set:PropertyName("createdAt") var createdAt: Long = 0L,
    @get:PropertyName("updatedAt") @set:PropertyName("updatedAt") var updatedAt: Long = 0L,
    @get:PropertyName("createdBy") @set:PropertyName("createdBy") var createdBy: String = "",
    @get:PropertyName("remoteVersion") @set:PropertyName("remoteVersion") var remoteVersion: Int = 0,
    @get:PropertyName("deleted") @set:PropertyName("deleted") var deleted: Boolean = false
) {
    fun toEntity(syncStatus: SyncStatus = SyncStatus.SYNCED): WorkspaceEntity {
        return WorkspaceEntity(
            id = id,
            name = name,
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
