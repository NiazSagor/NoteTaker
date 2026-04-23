package com.example.notetaker.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.notetaker.core.domain.model.SyncStatus

@Entity(tableName = "workspaces")
data class WorkspaceEntity(
    @PrimaryKey val id: String,                // UUID, shared across all users
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val createdBy: String,                     // Firebase UID
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
    val localVersion: Int = 0,
    val remoteVersion: Int = 0,
    val isDeleted: Boolean = false,

    // DEBUG fields
    val lastSyncAttemptAt: Long? = null,       // DEBUG: when did we last try to sync
    val lastSyncError: String? = null          // DEBUG: last error message if any
)
