package com.readassistant.core.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "articles",
    foreignKeys = [ForeignKey(
        entity = FeedEntity::class,
        parentColumns = ["id"],
        childColumns = ["feedId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("feedId"), Index("link")]
)
data class ArticleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val feedId: Long,
    val title: String = "",
    val link: String = "",
    val description: String = "",
    val content: String = "",
    val extractedContent: String? = null,
    val imageUrl: String? = null,
    val author: String? = null,
    val publishedAt: Long = 0,
    val isRead: Boolean = false,
    val isStarred: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
