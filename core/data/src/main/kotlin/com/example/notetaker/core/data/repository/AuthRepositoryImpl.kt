package com.example.notetaker.core.data.repository

import com.example.notetaker.core.domain.di.IoDispatcher
import com.example.notetaker.core.domain.repository.AuthRepository
import com.example.notetaker.core.network.firebase.AuthSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authSource: AuthSource,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : AuthRepository {

    private val _userId = MutableStateFlow(authSource.currentUser?.uid)
    
    override val currentUserId: String?
        get() = authSource.currentUser?.uid

    override fun observeUserId(): Flow<String?> = _userId.asStateFlow()

    override suspend fun signInAnonymously(): Result<String> = withContext(ioDispatcher) {
        try {
            val user = authSource.signInAnonymously()
            if (user != null) {
                _userId.value = user.uid
                Result.success(user.uid)
            } else {
                Result.failure(Exception("Failed to sign in anonymously"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
