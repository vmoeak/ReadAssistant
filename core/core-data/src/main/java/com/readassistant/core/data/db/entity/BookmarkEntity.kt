package com.readassistant.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contentType: String,
    val contentId: Long,
    val position: String = "",
    val title: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
