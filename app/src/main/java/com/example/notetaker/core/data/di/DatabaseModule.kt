package com.example.notetaker.core.data.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.notetaker.core.data.db.AppDatabase
import com.example.notetaker.core.data.db.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "notetaker.db"
        )
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .build()
    }

    @Provides
    fun provideWorkspaceDao(db: AppDatabase): WorkspaceDao = db.workspaceDao()

    @Provides
    fun provideGridElementDao(db: AppDatabase): GridElementDao = db.gridElementDao()

    @Provides
    fun provideNoteDao(db: AppDatabase): NoteDao = db.noteDao()

    @Provides
    fun provideNoteImageDao(db: AppDatabase): NoteImageDao = db.noteImageDao()

    @Provides
    fun provideConflictDao(db: AppDatabase): ConflictDao = db.conflictDao()
}
