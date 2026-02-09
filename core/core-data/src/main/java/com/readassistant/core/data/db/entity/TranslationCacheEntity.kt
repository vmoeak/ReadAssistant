package com.readassistant.core.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "translation_cache",
    indices = [Index(value = ["contentHash", "sourceLang", "targetLang"], unique = true)]
)
data class TranslationCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contentHash: String,
    val sourceLang: String,
    val targetLang: String,
    val originalText: String,
    val translatedText: String,
    val createdAt: Long = System.currentTimeMillis()
)
