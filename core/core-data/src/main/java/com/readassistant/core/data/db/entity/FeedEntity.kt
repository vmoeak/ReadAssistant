package com.readassistant.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "feeds")
data class FeedEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String = "",
    val description: String = "",
    val imageUrl: String? = null,
    val category: String = "",
    val unreadCount: Int = 0,
    val lastFetchedAt: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
)
