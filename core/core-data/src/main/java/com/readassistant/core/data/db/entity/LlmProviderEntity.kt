package com.readassistant.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "llm_providers")
data class LlmProviderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val providerType: String,
    val apiKey: String = "",
    val baseUrl: String = "",
    val modelName: String = "",
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
