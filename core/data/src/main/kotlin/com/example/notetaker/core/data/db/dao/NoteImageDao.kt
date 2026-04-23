package com.example.notetaker.core.data.db.dao

import androidx.room.*
import com.example.notetaker.core.data.db.entity.NoteImageEntity
import com.example.notetaker.core.domain.model.SyncStatus
import com.example.notetaker.core.domain.model.UploadStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Dao
interface NoteImageDao {
    @Query("SELECT * FROM note_images WHERE noteId = :noteId AND isDeleted = 0 ORDER BY orderInNote ASC")
    fun observeNoteImages(noteId: String): Flow<List<NoteImageEntity>>

    @Query("SELECT * FROM note_images WHERE id = :id")
    suspend fun getById(id: String): NoteImageEntity?

    @Upsert
    suspend fun upsert(image: NoteImageEntity)

    @Transaction
    @Upsert
    suspend fun upsertAll(images: List<NoteImageEntity>)

    @Query("UPDATE note_images SET isDeleted = 1, syncStatus = 'PENDING' WHERE id = :id")
    suspend fun softDelete(id: String)

    @Query("UPDATE note_images SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: SyncStatus)

    // Implement JSON conversion for conflict snapshots using kotlinx.serialization
    @Transaction
    @Query("SELECT * FROM note_images WHERE id = :id")
    suspend fun getNoteImageForSnapshot(id: String): NoteImageEntity? {
        return getById(id)
    }

    fun convertNoteImageEntityToJson(image: NoteImageEntity): String {
        return Json.encodeToString(image)
    }

    // Method to query for images that need upload or re-upload
    @Query("SELECT * FROM note_images WHERE uploadStatus IN ('PENDING', 'FAILED') AND localImageUri IS NOT NULL")
    suspend fun getImagesToUpload(): List<NoteImageEntity>

    // Method to update upload status
    @Query("UPDATE note_images SET uploadStatus = :status WHERE id = :id")
    suspend fun updateUploadStatus(id: String, status: UploadStatus)
}
