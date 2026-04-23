package com.example.notetaker.core.domain.usecase.auth

import com.example.notetaker.core.domain.base.ObservableUseCase
import com.example.notetaker.core.domain.di.MainDispatcher
import com.example.notetaker.core.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveUserIdUseCase @Inject constructor(
    private val repository: AuthRepository,
    @MainDispatcher dispatcher: CoroutineDispatcher
) : ObservableUseCase<Unit, String?>(dispatcher) {
    override fun execute(parameters: Unit): Flow<String?> {
        return repository.observeUserId()
    }
}
