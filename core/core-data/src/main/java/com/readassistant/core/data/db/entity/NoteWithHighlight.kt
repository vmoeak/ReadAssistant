package com.readassistant.core.data.db.entity

import androidx.room.Embedded
import androidx.room.Relation

data class NoteWithHighlight(
    @Embedded val note: NoteEntity,
    @Relation(
        parentColumn = "highlightId",
        entityColumn = "id"
    )
    val highlight: HighlightEntity?
)
