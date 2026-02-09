package com.readassistant.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "highlights")
data class HighlightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contentType: String,
    val contentId: Long,
    val selectedText: String,
    val startOffset: Int = 0,
    val endOffset: Int = 0,
    val paragraphIndex: Int = 0,
    val color: String = "yellow",
    val createdAt: Long = System.currentTimeMillis()
)
