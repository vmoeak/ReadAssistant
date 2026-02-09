package com.readassistant.core.data.db.dao

import androidx.room.*
import com.readassistant.core.data.db.entity.WebArticleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WebArticleDao {
    @Query("SELECT * FROM web_articles ORDER BY savedAt DESC")
    fun getAllArticles(): Flow<List<WebArticleEntity>>

    @Query("SELECT * FROM web_articles WHERE id = :id")
    suspend fun getArticleById(id: Long): WebArticleEntity?

    @Query("SELECT * FROM web_articles WHERE url = :url")
    suspend fun getArticleByUrl(url: String): WebArticleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(article: WebArticleEntity): Long

    @Update
    suspend fun update(article: WebArticleEntity)

    @Delete
    suspend fun delete(article: WebArticleEntity)
}
