package com.example.notetaker.core.data.db.dao

import androidx.room.*
import com.example.notetaker.core.data.db.entity.NoteImageEntity
import kotlinx.coroutines.flow.Flow

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
}
