package com.example.notetaker.core.domain.usecase.image

import com.example.notetaker.core.domain.model.NoteImage
import com.example.notetaker.core.domain.base.ObservableUseCase
import com.example.notetaker.core.domain.di.IoDispatcher
import com.example.notetaker.core.domain.repository.NoteImageRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveNoteImagesUseCase @Inject constructor(
    private val repository: NoteImageRepository,
    @IoDispatcher dispatcher: CoroutineDispatcher
) : ObservableUseCase<String, List<NoteImage>>(dispatcher) {
    override fun execute(parameters: String): Flow<List<NoteImage>> {
        return repository.observeNoteImages(parameters)
    }
}
