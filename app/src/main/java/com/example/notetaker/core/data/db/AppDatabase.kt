package com.example.notetaker.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.notetaker.core.data.db.dao.*
import com.example.notetaker.core.data.db.entity.*
import com.example.notetaker.core.domain.model.GridElementType
import com.example.notetaker.core.domain.model.ResolutionStrategy
import com.example.notetaker.core.domain.model.SyncStatus
import com.example.notetaker.core.domain.model.UploadStatus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
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
@TypeConverters(AppTypeConverters::class) // Use our custom converters
abstract class AppDatabase : RoomDatabase() {
    abstract fun workspaceDao(): WorkspaceDao
    abstract fun gridElementDao(): GridElementDao
    abstract fun noteDao(): NoteDao
    abstract fun noteImageDao(): NoteImageDao
    abstract fun conflictDao(): ConflictDao
}
