package com.example.notetaker.core.domain.usecase.image

import com.example.notetaker.core.data.db.entity.GridElementEntity
import com.example.notetaker.core.domain.base.UseCase
import com.example.notetaker.core.domain.di.IoDispatcher
import com.example.notetaker.core.domain.model.GridElementType
import com.example.notetaker.core.domain.model.SyncStatus
import com.example.notetaker.core.domain.model.UploadStatus
import com.example.notetaker.core.domain.repository.GridElementRepository
import kotlinx.coroutines.CoroutineDispatcher
import java.util.UUID
import javax.inject.Inject

data class AddStandaloneImageParams(
    val workspaceId: String,
    val localUri: String,
    val userId: String,
    val orderIndex: Double
)

class AddStandaloneImageUseCase @Inject constructor(
    private val repository: GridElementRepository,
    @IoDispatcher dispatcher: CoroutineDispatcher
) : UseCase<AddStandaloneImageParams, Unit>(dispatcher) {
    override suspend fun execute(parameters: AddStandaloneImageParams) {
        val now = System.currentTimeMillis()
        val element = GridElementEntity(
            id = UUID.randomUUID().toString(),
            workspaceId = parameters.workspaceId,
            type = GridElementType.STANDALONE_IMAGE,
            orderIndex = parameters.orderIndex,
            createdAt = now,
            updatedAt = now,
            createdBy = parameters.userId,
            localImageUri = parameters.localUri,
            uploadStatus = UploadStatus.PENDING,
            syncStatus = SyncStatus.PENDING
        )
        repository.saveGridElement(element)
    }
}
