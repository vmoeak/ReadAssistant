package com.readassistant.core.data.db.dao

import androidx.room.*
import com.readassistant.core.data.db.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE contentType = :type AND contentId = :id ORDER BY createdAt DESC")
    fun getBookmarks(type: String, id: String): Flow<List<BookmarkEntity>>

    @Insert
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Delete
    suspend fun delete(bookmark: BookmarkEntity)
}
