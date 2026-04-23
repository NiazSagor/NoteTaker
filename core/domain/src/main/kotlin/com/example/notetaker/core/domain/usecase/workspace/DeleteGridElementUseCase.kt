package com.example.notetaker.core.domain.usecase.workspace

import com.example.notetaker.core.domain.base.UseCase
import com.example.notetaker.core.domain.di.IoDispatcher
import com.example.notetaker.core.domain.repository.GridElementRepository
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

class DeleteGridElementUseCase @Inject constructor(
    private val repository: GridElementRepository,
    @IoDispatcher dispatcher: CoroutineDispatcher
) : UseCase<String, Unit>(dispatcher) {
    override suspend fun execute(parameters: String) {
        repository.softDeleteGridElement(parameters)
    }
}
