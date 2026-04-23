package com.example.notetaker.core.data.di

import com.example.notetaker.core.data.repository.*
import com.example.notetaker.core.domain.repository.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindWorkspaceRepository(
        workspaceRepositoryImpl: WorkspaceRepositoryImpl
    ): WorkspaceRepository

    @Binds
    @Singleton
    abstract fun bindNoteRepository(
        noteRepositoryImpl: NoteRepositoryImpl
    ): NoteRepository

    @Binds
    @Singleton
    abstract fun bindGridElementRepository(
        gridElementRepositoryImpl: GridElementRepositoryImpl
    ): GridElementRepository

    @Binds
    @Singleton
    abstract fun bindNoteImageRepository(
        noteImageRepositoryImpl: NoteImageRepositoryImpl
    ): NoteImageRepository

    @Binds
    @Singleton
    abstract fun bindConflictRepository(
        conflictRepositoryImpl: ConflictRepositoryImpl
    ): ConflictRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        authRepositoryImpl: AuthRepositoryImpl
    ): AuthRepository
}
