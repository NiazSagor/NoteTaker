package com.example.notetaker.core.data.di

import com.example.notetaker.core.data.repository.GridElementRepositoryImpl
import com.example.notetaker.core.data.repository.NoteRepositoryImpl
import com.example.notetaker.core.data.repository.WorkspaceRepositoryImpl
import com.example.notetaker.core.domain.repository.GridElementRepository
import com.example.notetaker.core.domain.repository.NoteRepository
import com.example.notetaker.core.domain.repository.WorkspaceRepository
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
}
