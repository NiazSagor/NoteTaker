package com.example.notetaker.core.domain.usecase.conflict

import com.example.notetaker.core.domain.base.UseCase
import com.example.notetaker.core.domain.di.IoDispatcher
import com.example.notetaker.core.domain.model.ResolutionStrategy
import com.example.notetaker.core.domain.repository.ConflictRepository
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

data class ResolveConflictParams(
    val conflictId: String,
    val strategy: ResolutionStrategy,
    val userId: String
)

class ResolveConflictUseCase @Inject constructor(
    private val repository: ConflictRepository,
    @IoDispatcher dispatcher: CoroutineDispatcher
) : UseCase<ResolveConflictParams, Unit>(dispatcher) {
    override suspend fun execute(parameters: ResolveConflictParams) {
        repository.resolveConflict(
            id = parameters.conflictId,
            strategy = parameters.strategy,
            userId = parameters.userId
        )
    }
}
