package com.example.notetaker.core.data.db.dao

import androidx.room.*
import com.example.notetaker.core.data.db.entity.GridElementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GridElementDao {
    @Query("SELECT * FROM grid_elements WHERE workspaceId = :workspaceId AND isDeleted = 0 ORDER BY orderIndex ASC")
    fun observeGridElements(workspaceId: String): Flow<List<GridElementEntity>>

    @Query("SELECT * FROM grid_elements WHERE id = :id")
    suspend fun getById(id: String): GridElementEntity?

    @Upsert
    suspend fun upsert(element: GridElementEntity)

    @Transaction
    @Upsert
    suspend fun upsertAll(elements: List<GridElementEntity>)

    @Query("UPDATE grid_elements SET isDeleted = 1, syncStatus = 'PENDING' WHERE id = :id")
    suspend fun softDelete(id: String)
}
