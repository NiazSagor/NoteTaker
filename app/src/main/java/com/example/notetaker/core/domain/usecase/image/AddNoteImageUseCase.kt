package com.example.notetaker.core.domain.usecase.image

import com.example.notetaker.core.data.db.entity.NoteImageEntity
import com.example.notetaker.core.domain.base.UseCase
import com.example.notetaker.core.domain.di.IoDispatcher
import com.example.notetaker.core.domain.model.SyncStatus
import com.example.notetaker.core.domain.model.UploadStatus
import com.example.notetaker.core.domain.repository.NoteImageRepository
import kotlinx.coroutines.CoroutineDispatcher
import java.util.UUID
import javax.inject.Inject

data class AddNoteImageParams(
    val noteId: String,
    val workspaceId: String,
    val localUri: String,
    val userId: String,
    val orderInNote: Int
)

class AddNoteImageUseCase @Inject constructor(
    private val repository: NoteImageRepository,
    @IoDispatcher dispatcher: CoroutineDispatcher
) : UseCase<com.example.notetaker.core.domain.usecase.image.AddNoteImageParams, Unit>(dispatcher) {
    override suspend fun execute(parameters: com.example.notetaker.core.domain.usecase.image.AddNoteImageParams) {
        val now = System.currentTimeMillis()
        val image = NoteImageEntity(
            id = UUID.randomUUID().toString(),
            noteId = parameters.noteId,
            workspaceId = parameters.workspaceId,
            orderInNote = parameters.orderInNote,
            localImageUri = parameters.localUri,
            remoteImageUrl = null,
            uploadStatus = UploadStatus.PENDING,
            createdAt = now,
            updatedAt = now,
            createdBy = parameters.userId,
            syncStatus = SyncStatus.PENDING
        )
        repository.saveNoteImage(image)
    }
}
