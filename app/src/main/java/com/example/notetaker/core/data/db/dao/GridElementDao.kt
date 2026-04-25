package com.example.notetaker.core.data.db.dao

import androidx.room.*
import com.example.notetaker.core.data.db.entity.GridElementEntity
import com.example.notetaker.core.data.db.entity.GridElementWithContent
import com.example.notetaker.core.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Dao
interface GridElementDao {
    @Transaction
    @Query("SELECT * FROM grid_elements WHERE workspaceId = :workspaceId AND deleted = 0 ORDER BY orderIndex ASC")
    fun observeGridElementsWithContent(workspaceId: String): Flow<List<GridElementWithContent>>

    @Query("SELECT * FROM grid_elements WHERE id = :id")
    suspend fun getById(id: String): GridElementEntity?

    @Upsert
    suspend fun upsert(element: GridElementEntity)

    @Transaction
    @Upsert
    suspend fun upsertAll(elements: List<GridElementEntity>)

    @Query("UPDATE grid_elements SET deleted = 1, syncStatus = 'PENDING' WHERE id = :id")
    suspend fun softDelete(id: String)

    @Query("UPDATE grid_elements SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: SyncStatus)

    @Query("DELETE FROM grid_elements WHERE id = :id")
    suspend fun deleteById(id: String)

    // Implement JSON conversion for conflict snapshots using kotlinx.serialization
    @Transaction
    @Query("SELECT * FROM grid_elements WHERE id = :id")
    suspend fun getGridElementForSnapshot(id: String): GridElementEntity? {
        return getById(id)
    }

    fun convertGridElementEntityToJson(element: GridElementEntity): String {
        return Json.encodeToString(element)
    }

    // Method to query for pending or errored grid elements
    @Query("SELECT * FROM grid_elements WHERE syncStatus IN ('PENDING', 'ERROR')")
    suspend fun getPendingOrErrorGridElements(): List<GridElementEntity>
}
