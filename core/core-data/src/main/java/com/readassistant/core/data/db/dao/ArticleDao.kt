package com.readassistant.core.data.db.dao

import androidx.room.*
import com.readassistant.core.data.db.entity.ArticleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {
    @Query("SELECT * FROM articles WHERE feedId = :feedId ORDER BY publishedAt DESC")
    fun getArticlesByFeed(feedId: Long): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE isStarred = 1 ORDER BY publishedAt DESC")
    fun getStarredArticles(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE id = :id")
    suspend fun getArticleById(id: Long): ArticleEntity?

    @Query("SELECT * FROM articles WHERE link = :link")
    suspend fun getArticleByLink(link: String): ArticleEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(articles: List<ArticleEntity>)

    @Update
    suspend fun update(article: ArticleEntity)

    @Query("UPDATE articles SET isRead = :isRead WHERE id = :id")
    suspend fun updateReadStatus(id: Long, isRead: Boolean)

    @Query("UPDATE articles SET isStarred = :isStarred WHERE id = :id")
    suspend fun updateStarredStatus(id: Long, isStarred: Boolean)

    @Query("UPDATE articles SET extractedContent = :content WHERE id = :id")
    suspend fun updateExtractedContent(id: Long, content: String)

    @Query("DELETE FROM articles WHERE feedId = :feedId")
    suspend fun deleteByFeed(feedId: Long)

    @Query("SELECT COUNT(*) FROM articles WHERE feedId = :feedId AND isRead = 0")
    suspend fun getUnreadCount(feedId: Long): Int
}
