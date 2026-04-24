package com.example.notetaker.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.notetaker.core.domain.model.ResolutionStrategy

@Entity(tableName = "conflicts")
data class ConflictEntity(
    @PrimaryKey val id: String,                // UUID
    val noteId: String,                        // which note has conflict
    val workspaceId: String,
    val localSnapshot: String,                 // JSON of local NoteEntity
    val remoteSnapshot: String,                // JSON of incoming remote NoteEntity
    val localVersion: Int,
    val remoteVersion: Int,
    val expectedVersion: Int,
    val conflictRoundCount: Int = 0,
    val detectedAt: Long,
    val resolvedAt: Long? = null,
    val isResolved: Boolean = false,
    val resolutionStrategy: ResolutionStrategy? = null,
    val resolvedBy: String? = null,            // Firebase UID of who resolved

    // DEBUG fields
    val conflictDiffSummary: String? = null    // DEBUG: short human-readable diff
)
