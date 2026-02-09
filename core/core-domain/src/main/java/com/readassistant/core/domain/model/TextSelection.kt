package com.readassistant.core.domain.model

data class TextSelection(
    val selectedText: String,
    val startOffset: Int = 0,
    val endOffset: Int = 0,
    val paragraphIndex: Int = 0,
    val rect: SelectionRect? = null
)

data class SelectionRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)
