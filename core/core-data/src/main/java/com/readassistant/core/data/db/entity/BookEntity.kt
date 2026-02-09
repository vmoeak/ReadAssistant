package com.readassistant.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filePath: String,
    val format: String,
    val title: String = "",
    val author: String = "",
    val coverPath: String? = null,
    val totalChapters: Int = 0,
    val fileSize: Long = 0,
    val addedAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long = 0
)
