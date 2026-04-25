package com.example.notetaker.core.domain.usecase.note

import com.example.notetaker.core.domain.model.Note
import com.example.notetaker.core.domain.base.ObservableUseCase
import com.example.notetaker.core.domain.di.IoDispatcher
import com.example.notetaker.core.domain.repository.NoteRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveNoteUseCase @Inject constructor(
    private val repository: NoteRepository,
    @IoDispatcher dispatcher: CoroutineDispatcher
) : ObservableUseCase<String, Note?>(dispatcher) {
    override fun execute(parameters: String): Flow<Note?> {
        return repository.observeNote(parameters)
    }
}
