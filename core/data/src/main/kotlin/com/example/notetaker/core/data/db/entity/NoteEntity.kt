package com.example.notetaker.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.notetaker.core.domain.model.SyncStatus

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,                // UUID — same as GridElementEntity.noteId
    val workspaceId: String,
    val title: String,
    val content: String,                       // plain text for now
    val createdAt: Long,
    val updatedAt: Long,
    val createdBy: String,                     // Firebase UID
    val lastEditedBy: String,                  // Firebase UID of last editor

    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val localVersion: Int = 0,
    val remoteVersion: Int = 0,
    val isDeleted: Boolean = false,

    // Conflict snapshots — stored locally when conflict detected
    // Null when no conflict
    val conflictLocalSnapshot: String? = null,   // JSON of local NoteEntity at conflict time
    val conflictRemoteSnapshot: String? = null,  // JSON of incoming remote NoteEntity
    val conflictDetectedAt: Long? = null,

    // DEBUG fields
    val lastSyncAttemptAt: Long? = null,
    val lastSyncError: String? = null,
    val editCount: Int = 0                     // DEBUG: how many times edited locally
)
