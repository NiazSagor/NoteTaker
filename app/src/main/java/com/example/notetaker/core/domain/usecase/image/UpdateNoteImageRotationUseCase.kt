package com.example.notetaker.core.domain.usecase.image

import com.example.notetaker.core.data.db.dao.NoteImageDao
import com.example.notetaker.core.domain.base.UseCase
import com.example.notetaker.core.domain.di.IoDispatcher
import com.example.notetaker.core.domain.model.SyncStatus
import com.example.notetaker.core.domain.repository.NoteImageRepository
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

data class UpdateRotationParams(
    val imageId: String,
    val rotationDegrees: Float
)

class UpdateNoteImageRotationUseCase @Inject constructor(
    private val noteImageDao: NoteImageDao,
    private val repository: NoteImageRepository,
    @IoDispatcher dispatcher: CoroutineDispatcher
) : UseCase<UpdateRotationParams, Unit>(dispatcher) {
    override suspend fun execute(parameters: UpdateRotationParams) {
        val image = noteImageDao.getById(parameters.imageId) ?: return
        val updatedImage = image.copy(
            rotationDegrees = parameters.rotationDegrees,
            updatedAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING,
            localVersion = image.localVersion + 1
        )
        repository.updateRotation(updatedImage)
    }
}
