package com.example.notetaker.core.domain.usecase.note

import com.example.notetaker.core.data.db.entity.GridElementEntity
import com.example.notetaker.core.data.db.entity.NoteEntity
import com.example.notetaker.core.domain.base.UseCase
import com.example.notetaker.core.domain.di.IoDispatcher
import com.example.notetaker.core.domain.model.GridElementType
import com.example.notetaker.core.domain.model.SyncStatus
import com.example.notetaker.core.domain.repository.GridElementRepository
import com.example.notetaker.core.domain.repository.NoteRepository
import kotlinx.coroutines.CoroutineDispatcher
import java.util.UUID
import javax.inject.Inject

data class CreateNoteParams(
    val workspaceId: String,
    val userId: String,
    val orderIndex: Double
)

class CreateNoteUseCase @Inject constructor(
    private val noteRepository: NoteRepository,
    private val gridElementRepository: GridElementRepository,
    @IoDispatcher dispatcher: CoroutineDispatcher
) : UseCase<com.example.notetaker.core.domain.usecase.note.CreateNoteParams, String>(dispatcher) {
    override suspend fun execute(parameters: com.example.notetaker.core.domain.usecase.note.CreateNoteParams): String {
        val noteId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val note = NoteEntity(
            id = noteId,
            workspaceId = parameters.workspaceId,
            title = "",
            content = "",
            createdAt = now,
            updatedAt = now,
            createdBy = parameters.userId,
            lastEditedBy = parameters.userId,
            syncStatus = SyncStatus.PENDING
        )

        val gridElement = GridElementEntity(
            id = UUID.randomUUID().toString(),
            workspaceId = parameters.workspaceId,
            type = GridElementType.NOTE,
            orderIndex = parameters.orderIndex,
            createdAt = now,
            updatedAt = now,
            createdBy = parameters.userId,
            noteId = noteId,
            syncStatus = SyncStatus.PENDING
        )

        noteRepository.saveNote(note)
        gridElementRepository.saveGridElement(gridElement)

        return noteId
    }
}
