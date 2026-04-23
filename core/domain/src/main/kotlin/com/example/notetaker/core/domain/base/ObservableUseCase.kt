package com.example.notetaker.core.domain.base

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Base class for stream-based UseCases (Flow).
 * [P] Parameters type.
 * [R] Result type.
 */
abstract class ObservableUseCase<in P, out R>(private val dispatcher: CoroutineDispatcher) {
    operator fun invoke(parameters: P): Flow<Result<R>> {
        return execute(parameters)
            .map { Result.Success(it) as Result<R> }
            .catch { emit(Result.Error(it)) }
            .flowOn(dispatcher)
    }

    protected abstract fun execute(parameters: P): Flow<R>
}
