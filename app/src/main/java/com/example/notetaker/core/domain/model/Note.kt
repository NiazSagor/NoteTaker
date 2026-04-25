package com.example.notetaker.core.domain.model

import com.example.notetaker.core.data.db.entity.NoteEntity
import kotlinx.serialization.Serializable

@Serializable
data class Note(
    val id: String,
    val workspaceId: String,
    val title: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    val createdBy: String,
    val lastEditedBy: String,
    val remoteVersion: Int,
    val syncStatus: SyncStatus,
    val deleted: Boolean
) {
    fun toEntity(localVersion: Int = 0): NoteEntity {
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
            syncStatus = syncStatus,
            localVersion = localVersion,
            deleted = deleted
        )
    }
}
