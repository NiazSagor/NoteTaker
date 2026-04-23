package com.example.notetaker.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.notetaker.core.data.db.converters.Converters
import com.example.notetaker.core.data.db.dao.*
import com.example.notetaker.core.data.db.entity.*

@Database(
    entities = [
        WorkspaceEntity::class,
        GridElementEntity::class,
        NoteEntity::class,
        NoteImageEntity::class,
        ConflictEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workspaceDao(): WorkspaceDao
    abstract fun gridElementDao(): GridElementDao
    abstract fun noteDao(): NoteDao
    abstract fun noteImageDao(): NoteImageDao
    abstract fun conflictDao(): ConflictDao
}
