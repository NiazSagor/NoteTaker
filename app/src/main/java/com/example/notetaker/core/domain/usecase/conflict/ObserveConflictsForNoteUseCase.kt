package com.example.notetaker.core.domain.usecase.conflict

import com.example.notetaker.core.data.db.entity.ConflictEntity
import com.example.notetaker.core.domain.base.ObservableUseCase
import com.example.notetaker.core.domain.di.IoDispatcher
import com.example.notetaker.core.domain.model.Conflict
import com.example.notetaker.core.domain.repository.ConflictRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveConflictsForNoteUseCase @Inject constructor(
    private val repository: ConflictRepository,
    @IoDispatcher dispatcher: CoroutineDispatcher
) : ObservableUseCase<String, List<Conflict>>(dispatcher) {
    override fun execute(parameters: String): Flow<List<Conflict>> {
        return repository.observeConflictsForNote(parameters)
    }
}
