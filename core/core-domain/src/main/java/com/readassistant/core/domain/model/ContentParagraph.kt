package com.readassistant.core.domain.model

data class ContentParagraph(
    val index: Int,
    val html: String,
    val plainText: String,
    val type: ParagraphType = ParagraphType.TEXT
)

enum class ParagraphType {
    TEXT, HEADING, IMAGE, CODE, QUOTE, LIST
}
