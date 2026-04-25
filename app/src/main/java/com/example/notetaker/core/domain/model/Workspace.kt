package com.example.notetaker.core.domain.model

import com.example.notetaker.core.data.db.entity.WorkspaceEntity

data class Workspace(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val createdBy: String,
    val syncStatus: SyncStatus,
    val remoteVersion: Int,
    val deleted: Boolean
) {
    fun toEntity(localVersion: Int = 0): WorkspaceEntity {
        return WorkspaceEntity(
            id = id,
            name = name,
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
