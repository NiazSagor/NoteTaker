package com.example.notetaker.core.data.db.converters

import androidx.room.TypeConverter
import com.example.notetaker.core.domain.model.GridElementType
import com.example.notetaker.core.domain.model.ResolutionStrategy
import com.example.notetaker.core.domain.model.SyncStatus
import com.example.notetaker.core.domain.model.UploadStatus

class Converters {
    @TypeConverter
    fun fromSyncStatus(value: SyncStatus): String = value.name
    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus = SyncStatus.valueOf(value)

    @TypeConverter
    fun fromGridElementType(value: GridElementType): String = value.name
    @TypeConverter
    fun toGridElementType(value: String): GridElementType = GridElementType.valueOf(value)

    @TypeConverter
    fun fromUploadStatus(value: UploadStatus): String = value.name
    @TypeConverter
    fun toUploadStatus(value: String): UploadStatus = UploadStatus.valueOf(value)

    @TypeConverter
    fun fromResolutionStrategy(value: ResolutionStrategy?): String? = value?.name
    @TypeConverter
    fun toResolutionStrategy(value: String?): ResolutionStrategy? = value?.let { ResolutionStrategy.valueOf(it) }
}
