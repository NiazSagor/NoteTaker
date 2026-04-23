package com.example.notetaker.core.domain.usecase.auth

import com.example.notetaker.core.domain.base.UseCase
import com.example.notetaker.core.domain.di.IoDispatcher
import com.example.notetaker.core.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

class SignInAnonymouslyUseCase @Inject constructor(
    private val repository: AuthRepository,
    @IoDispatcher dispatcher: CoroutineDispatcher
) : UseCase<Unit, String>(dispatcher) {
    override suspend fun execute(parameters: Unit): String {
        val result = repository.signInAnonymously()
        return result.getOrThrow()
    }
}
