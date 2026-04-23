package com.example.notetaker.core.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppScopeModule {

    @Provides
    @Singleton
    fun providesAppCoroutineScope(): CoroutineScope {
        // This scope will live as long as the application
        return CoroutineScope(Dispatchers.Default) // Use Dispatchers.Default or other appropriate dispatcher
    }
}
