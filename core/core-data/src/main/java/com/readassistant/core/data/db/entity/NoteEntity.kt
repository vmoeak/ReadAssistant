package com.readassistant.core.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notes",
    foreignKeys = [ForeignKey(
        entity = HighlightEntity::class,
        parentColumns = ["id"],
        childColumns = ["highlightId"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [Index("highlightId")]
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val highlightId: Long? = null,
    val contentType: String = "",
    val contentId: Long = 0,
    val noteText: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
