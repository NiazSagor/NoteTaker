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

// Note: This converter applies to multiple entities. If specific conversion is needed per entity,
// it might be better to have entity-specific converters.
class AppTypeConverters {
    // SyncStatus
    @TypeConverter
    fun fromSyncStatus(value: SyncStatus): String = value.name
    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus = SyncStatus.valueOf(value)

    // GridElementType
    @TypeConverter
    fun fromGridElementType(value: GridElementType): String = value.name
    @TypeConverter
    fun toGridElementType(value: String): GridElementType = GridElementType.valueOf(value)

    // UploadStatus
    @TypeConverter
    fun fromUploadStatus(value: UploadStatus): String = value.name
    @TypeConverter
    fun toUploadStatus(value: String): UploadStatus = UploadStatus.valueOf(value)

    // ResolutionStrategy
    @TypeConverter
    fun fromResolutionStrategy(value: ResolutionStrategy?): String? = value?.name
    @TypeConverter
    fun toResolutionStrategy(value: String?): ResolutionStrategy? = value?.let { ResolutionStrategy.valueOf(it) }

    // JSON Converters for Snapshots
    @TypeConverter
    fun fromNoteEntityJson(value: String): NoteEntity? = try {
        Json.decodeFromString<NoteEntity>(value)
    } catch (e: Exception) { null }

    @TypeConverter
    fun toNoteEntityJson(note: NoteEntity): String = Json.encodeToString(note)

    @TypeConverter
    fun fromGridElementEntityJson(value: String): GridElementEntity? = try {
        Json.decodeFromString<GridElementEntity>(value)
    } catch (e: Exception) { null }

    @TypeConverter
    fun toGridElementEntityJson(element: GridElementEntity): String = Json.encodeToString(element)

    @TypeConverter
    fun fromNoteImageEntityJson(value: String): NoteImageEntity? = try {
        Json.decodeFromString<NoteImageEntity>(value)
    } catch (e: Exception) { null }

    @TypeConverter
    fun toNoteImageEntityJson(image: NoteImageEntity): String = Json.encodeToString(image)
}
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
