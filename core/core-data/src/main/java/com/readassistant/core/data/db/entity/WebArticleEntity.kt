package com.readassistant.core.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "web_articles",
    indices = [Index("url")]
)
data class WebArticleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String = "",
    val content: String = "",
    val textContent: String = "",
    val imageUrl: String? = null,
    val siteName: String? = null,
    val savedAt: Long = System.currentTimeMillis()
)
