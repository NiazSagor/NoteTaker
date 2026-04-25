package com.example.notetaker.core.domain.repository

import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val currentUserId: String?
    fun observeUserId(): Flow<String?>
    suspend fun signInAnonymously(): Result<String>
}
