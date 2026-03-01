package com.readassistant.feature.reader.presentation.reader

import com.readassistant.feature.library.data.cache.BookParagraphCache

data class ReaderParagraph(
    val index: Int,
    val text: String,
    val isHeading: Boolean = false,
    val html: String = "",
    val imageSrc: String? = null,
    val plainText: String = "",
    val linkSpans: List<BookParagraphCache.CachedLinkSpan> = emptyList(),
    val anchorIds: List<String> = emptyList()
)
