package com.example.notetaker.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.notetaker.core.domain.model.Conflict
import com.example.notetaker.core.domain.model.Note
import com.example.notetaker.core.domain.model.ResolutionStrategy
import kotlinx.serialization.json.Json

@Entity(tableName = "conflicts")
data class ConflictEntity(
    @PrimaryKey
    val noteId: String = "",                        // which note has conflict
    val workspaceId: String = "",
    val localSnapshot: String? = null,                 // JSON of local NoteEntity at conflict time
    val remoteSnapshot: String? = null,                // JSON of incoming remote NoteEntity
    val localVersion: Int = 0,
    val remoteVersion: Int = 0,
    val expectedVersion: Int = 0,
    val conflictRoundCount: Int = 0,
    val detectedAt: Long = 0L,
    val resolvedAt: Long? = null,
    val isResolved: Boolean = false,
    val resolutionStrategy: ResolutionStrategy? = null,
    val resolvedBy: String? = null,            // Firebase UID of who resolved

    // DEBUG fields
    val conflictDiffSummary: String? = null    // DEBUG: short human-readable diff
) {
    fun toDomain(): Conflict {
        return Conflict(
            noteId = noteId,
            workspaceId = workspaceId,
            localNote = localSnapshot?.let { Json.decodeFromString<Note>(it) },
            remoteNote = remoteSnapshot?.let { Json.decodeFromString<Note>(it) },
            localVersion = localVersion,
            remoteVersion = remoteVersion,
            expectedVersion = expectedVersion,
            conflictRoundCount = conflictRoundCount,
            detectedAt = detectedAt,
            resolvedAt = resolvedAt,
            isResolved = isResolved,
            resolutionStrategy = resolutionStrategy,
            resolvedBy = resolvedBy,
            conflictDiffSummary = conflictDiffSummary
        )
    }
}

