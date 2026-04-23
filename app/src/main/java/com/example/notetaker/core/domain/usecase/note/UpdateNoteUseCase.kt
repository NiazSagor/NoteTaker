package com.example.notetaker.core.domain.usecase.note

import com.example.notetaker.core.domain.base.UseCase
import com.example.notetaker.core.domain.di.IoDispatcher
import com.example.notetaker.core.domain.model.SyncStatus
import com.example.notetaker.core.domain.repository.NoteRepository
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

data class UpdateNoteParams(
    val noteId: String,
    val title: String,
    val content: String,
    val userId: String
)

class UpdateNoteUseCase @Inject constructor(
    private val repository: NoteRepository,
    @IoDispatcher dispatcher: CoroutineDispatcher
) : UseCase<com.example.notetaker.core.domain.usecase.note.UpdateNoteParams, Unit>(dispatcher) {
    override suspend fun execute(parameters: com.example.notetaker.core.domain.usecase.note.UpdateNoteParams) {
        val note = repository.getNote(parameters.noteId) ?: return
        val updatedNote = note.copy(
            title = parameters.title,
            content = parameters.content,
            lastEditedBy = parameters.userId,
            updatedAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING,
            localVersion = note.localVersion + 1
        )
        repository.saveNote(updatedNote)
    }
}
