package com.readassistant.core.data.db.dao

import androidx.room.*
import com.readassistant.core.data.db.entity.FeedEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedDao {
    @Query("SELECT * FROM feeds ORDER BY title ASC")
    fun getAllFeeds(): Flow<List<FeedEntity>>

    @Query("SELECT * FROM feeds ORDER BY title ASC")
    suspend fun getAllFeedsSync(): List<FeedEntity>

    @Query("SELECT * FROM feeds WHERE id = :id")
    suspend fun getFeedById(id: Long): FeedEntity?

    @Query("SELECT * FROM feeds WHERE url = :url")
    suspend fun getFeedByUrl(url: String): FeedEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(feed: FeedEntity): Long

    @Update
    suspend fun update(feed: FeedEntity)

    @Delete
    suspend fun delete(feed: FeedEntity)

    @Query("UPDATE feeds SET unreadCount = :count WHERE id = :feedId")
    suspend fun updateUnreadCount(feedId: Long, count: Int)

    @Query("UPDATE feeds SET lastFetchedAt = :timestamp WHERE id = :feedId")
    suspend fun updateLastFetched(feedId: Long, timestamp: Long)
}
