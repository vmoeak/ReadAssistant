package com.readassistant.core.data.db.dao

import androidx.room.*
import com.readassistant.core.data.db.entity.HighlightEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HighlightDao {
    @Query("SELECT * FROM highlights WHERE contentType = :type AND contentId = :id ORDER BY createdAt DESC")
    fun getHighlights(type: String, id: String): Flow<List<HighlightEntity>>

    @Insert
    suspend fun insert(highlight: HighlightEntity): Long

    @Update
    suspend fun update(highlight: HighlightEntity)

    @Delete
    suspend fun delete(highlight: HighlightEntity)
}
