package com.readassistant.core.data.db.dao

import androidx.room.*
import com.readassistant.core.data.db.entity.ReadingProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingProgressDao {
    @Query("SELECT * FROM reading_progress WHERE contentType = :type AND contentId = :id")
    suspend fun getProgress(type: String, id: String): ReadingProgressEntity?

    @Query("SELECT * FROM reading_progress ORDER BY lastReadAt DESC")
    fun getRecentProgress(): Flow<List<ReadingProgressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: ReadingProgressEntity)

    @Query("DELETE FROM reading_progress WHERE contentType = :type AND contentId = :id")
    suspend fun delete(type: String, id: String)
}
