package com.example.notetaker.core.domain.usecase.workspace

import com.example.notetaker.core.domain.base.UseCase
import com.example.notetaker.core.domain.di.IoDispatcher
import com.example.notetaker.core.domain.model.SyncStatus
import com.example.notetaker.core.domain.repository.GridElementRepository
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

data class ReorderParams(
    val elementId: String,
    val newOrderIndex: Double
)

class ReorderGridElementUseCase @Inject constructor(
    private val repository: GridElementRepository,
    @IoDispatcher dispatcher: CoroutineDispatcher
) : UseCase<ReorderParams, Unit>(dispatcher) {
    override suspend fun execute(parameters: ReorderParams) {
        val element = repository.getGridElement(parameters.elementId) ?: return
        val updatedElement = element.copy(
            orderIndex = parameters.newOrderIndex,
            syncStatus = SyncStatus.PENDING,
            updatedAt = System.currentTimeMillis()
        )
        repository.saveGridElement(updatedElement)
    }
}
