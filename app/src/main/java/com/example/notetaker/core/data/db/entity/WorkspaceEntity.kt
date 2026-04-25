package com.example.notetaker.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.notetaker.core.domain.model.SyncStatus
import com.example.notetaker.core.domain.model.Workspace
import com.example.notetaker.core.network.firebase.model.WorkspaceDto

@Entity(tableName = "workspaces")
data class WorkspaceEntity(
    @PrimaryKey val id: String = "",                // UUID, shared across all users
    val name: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val createdBy: String = "",                     // Firebase UID
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
    val localVersion: Int = 0,
    val remoteVersion: Int = 0,
    val deleted: Boolean = false,

    // DEBUG fields
    val lastSyncAttemptAt: Long? = null,       // DEBUG: when did we last try to sync
    val lastSyncError: String? = null          // DEBUG: last error message if any
) {
    fun toDomain(): Workspace {
        return Workspace(
            id = id,
            name = name,
            createdAt = createdAt,
            updatedAt = updatedAt,
            createdBy = createdBy,
            syncStatus = syncStatus,
            remoteVersion = remoteVersion,
            deleted = deleted
        )
    }

    fun toDto(): WorkspaceDto {
        return WorkspaceDto(
            id = id,
            name = name,
            createdAt = createdAt,
            updatedAt = updatedAt,
            createdBy = createdBy,
            remoteVersion = remoteVersion,
            deleted = deleted
        )
    }
}
