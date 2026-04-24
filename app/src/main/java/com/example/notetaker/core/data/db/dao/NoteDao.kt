package com.example.notetaker.core.data.db.dao

import androidx.room.*
import com.example.notetaker.core.data.db.entity.NoteEntity
import com.example.notetaker.core.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE id = :id")
    fun observeNote(id: String): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNote(id: String): NoteEntity?

    @Upsert
    suspend fun upsert(note: NoteEntity)

    @Query("UPDATE notes SET deleted = 1, syncStatus = 'PENDING' WHERE id = :id")
    suspend fun softDelete(id: String)

    @Query("UPDATE notes SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: SyncStatus)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: String)

    // Implement JSON conversion for conflict snapshots using kotlinx.serialization
    @Transaction // Ensure atomicity if this method were more complex
    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteForSnapshot(id: String): NoteEntity? {
        return getNote(id) // Room automatically handles fetching the entity
    }

    fun convertNoteEntityToJson(note: NoteEntity): String {
        return Json.encodeToString(note)
    }

    // Method to query for pending or errored notes
    @Query("SELECT * FROM notes WHERE syncStatus IN ('PENDING', 'ERROR')")
    suspend fun getPendingOrErrorNotes(): List<NoteEntity>
}
