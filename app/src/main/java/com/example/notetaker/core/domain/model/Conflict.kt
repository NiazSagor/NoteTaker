package com.example.notetaker.core.domain.model

import com.example.notetaker.core.data.db.entity.ConflictEntity
import com.example.notetaker.core.domain.model.ResolutionStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable // Make the domain model serializable for potential future needs
data class Conflict(
    val id: String,
    val noteId: String,
    val workspaceId: String,
    val localNote: Note?, // Parsed from localSnapshot JSON
    val remoteNote: Note?, // Parsed from remoteSnapshot JSON
    val localVersion: Int,
    val remoteVersion: Int,
    val expectedVersion: Int,
    val conflictRoundCount: Int,
    val detectedAt: Long,
    val resolvedAt: Long?,
    val isResolved: Boolean,
    val resolutionStrategy: ResolutionStrategy?,
    val resolvedBy: String?,
    val conflictDiffSummary: String?
) {
    fun toEntity(): ConflictEntity {
        // Mapping back from domain model to entity.
        // Note: This assumes ConflictEntity's JSON fields are compatible with Note's serialization.
        // We'll serialize the Note domain models back to JSON for the entity.
        return ConflictEntity(
            id = id,
            noteId = noteId,
            workspaceId = workspaceId,
            localSnapshot = localNote?.let { Json.encodeToString(it) }, // Serialize Note domain model
            remoteSnapshot = remoteNote?.let { Json.encodeToString(it) }, // Serialize Note domain model
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
