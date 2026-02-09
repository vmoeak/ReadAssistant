package com.readassistant.core.domain.model

data class ReadingContent(
    val id: Long,
    val title: String,
    val contentType: ContentType,
    val chapters: List<Chapter> = emptyList()
)

enum class ContentType {
    RSS_ARTICLE, WEB_ARTICLE, EPUB, PDF, MOBI, AZW3, FB2, TXT, HTML, CBZ, CBR
}

data class Chapter(
    val index: Int,
    val title: String,
    val href: String = ""
)
