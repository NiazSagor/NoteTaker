package com.example.notetaker.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.notetaker.core.domain.model.SyncStatus

import com.example.notetaker.core.domain.model.Note
import com.example.notetaker.core.network.firebase.model.NoteDto

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String = "",                // UUID â€” same as GridElementEntity.noteId
    val workspaceId: String = "",
    val title: String = "",
    val content: String = "",                       // plain text for now
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val createdBy: String = "",                     // Firebase UID
    val lastEditedBy: String = "",                  // Firebase UID of last editor

    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val localVersion: Int = 0,
    val remoteVersion: Int = 0,
    val deleted: Boolean = false,

    // Conflict snapshots — stored locally when conflict detected
    // Null when no conflict
    val conflictLocalSnapshot: String? = null,   // JSON of local NoteEntity at conflict time
    val conflictRemoteSnapshot: String? = null,  // JSON of incoming remote NoteEntity
    val conflictDetectedAt: Long? = null,

    // DEBUG fields
    val lastSyncAttemptAt: Long? = null,
    val lastSyncError: String? = null,
    val editCount: Int = 0                     // DEBUG: how many times edited locally
) {
    fun toDomain(): Note {
        return Note(
            id = id,
            workspaceId = workspaceId,
            title = title,
            content = content,
            createdAt = createdAt,
            updatedAt = updatedAt,
            createdBy = createdBy,
            lastEditedBy = lastEditedBy,
            remoteVersion = remoteVersion,
            syncStatus = syncStatus,
            deleted = deleted
        )
    }

    fun toDto(): NoteDto {
        return NoteDto(
            id = id,
            workspaceId = workspaceId,
            title = title,
            content = content,
            createdAt = createdAt,
            updatedAt = updatedAt,
            createdBy = createdBy,
            lastEditedBy = lastEditedBy,
            remoteVersion = remoteVersion,
            deleted = deleted
        )
    }

    fun toFirestoreMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "workspaceId" to workspaceId,
            "title" to title,
            "content" to content,
            "createdAt" to createdAt,
            "updatedAt" to updatedAt,
            "createdBy" to createdBy,
            "lastEditedBy" to lastEditedBy,
            "remoteVersion" to remoteVersion,
            "deleted" to deleted
        )
    }
}
