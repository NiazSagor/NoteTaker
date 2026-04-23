package com.example.notetaker.core.domain.usecase.image

import com.example.notetaker.core.domain.base.UseCase
import com.example.notetaker.core.domain.di.IoDispatcher
import com.example.notetaker.core.domain.repository.NoteImageRepository
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

class DeleteNoteImageUseCase @Inject constructor(
    private val repository: NoteImageRepository,
    @IoDispatcher dispatcher: CoroutineDispatcher
) : UseCase<String, Unit>(dispatcher) {
    override suspend fun execute(parameters: String) {
        repository.softDeleteNoteImage(parameters)
    }
}
