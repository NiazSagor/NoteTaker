package com.example.notetaker.core.domain.usecase.workspace

import com.example.notetaker.core.data.db.entity.GridElementEntity
import com.example.notetaker.core.domain.base.ObservableUseCase
import com.example.notetaker.core.domain.di.IoDispatcher
import com.example.notetaker.core.domain.repository.GridElementRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetGridElementsUseCase @Inject constructor(
    private val repository: GridElementRepository,
    @IoDispatcher dispatcher: CoroutineDispatcher
) : ObservableUseCase<String, List<GridElementEntity>>(dispatcher) {
    override fun execute(parameters: String): Flow<List<GridElementEntity>> {
        return repository.observeGridElements(parameters)
    }
}
