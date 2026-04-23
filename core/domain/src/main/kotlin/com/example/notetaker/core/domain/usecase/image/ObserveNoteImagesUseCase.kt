package com.example.notetaker.core.domain.usecase.image

import com.example.notetaker.core.data.db.entity.NoteImageEntity
import com.example.notetaker.core.domain.base.ObservableUseCase
import com.example.notetaker.core.domain.di.IoDispatcher
import com.example.notetaker.core.domain.repository.NoteImageRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveNoteImagesUseCase @Inject constructor(
    private val repository: NoteImageRepository,
    @IoDispatcher dispatcher: CoroutineDispatcher
) : ObservableUseCase<String, List<NoteImageEntity>>(dispatcher) {
    override fun execute(parameters: String): Flow<List<NoteImageEntity>> {
        return repository.observeNoteImages(parameters)
    }
}
