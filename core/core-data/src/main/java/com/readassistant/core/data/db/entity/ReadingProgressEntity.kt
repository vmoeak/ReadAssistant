package com.readassistant.core.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reading_progress",
    indices = [Index(value = ["contentType", "contentId"], unique = true)]
)
data class ReadingProgressEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contentType: String,
    val contentId: Long,
    val chapterIndex: Int = 0,
    val scrollPosition: Int = 0,
    val cfi: String? = null,
    val progressPercent: Float = 0f,
    val updatedAt: Long = System.currentTimeMillis()
)
