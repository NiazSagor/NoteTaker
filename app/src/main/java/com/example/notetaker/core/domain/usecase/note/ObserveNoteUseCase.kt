package com.example.notetaker.core.domain.usecase.note

import com.example.notetaker.core.data.db.entity.NoteEntity
import com.example.notetaker.core.domain.base.ObservableUseCase
import com.example.notetaker.core.domain.di.IoDispatcher
import com.example.notetaker.core.domain.repository.NoteRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveNoteUseCase @Inject constructor(
    private val repository: NoteRepository,
    @IoDispatcher dispatcher: CoroutineDispatcher
) : ObservableUseCase<String, NoteEntity?>(dispatcher) {
    override fun execute(parameters: String): Flow<NoteEntity?> {
        return repository.observeNote(parameters)
    }
}
