package com.readassistant.feature.library.data.parser

import com.readassistant.feature.library.domain.BookFormat
import javax.inject.Inject
import javax.inject.Singleton

data class BookMetadata(val title: String, val author: String = "", val coverPath: String? = null, val totalChapters: Int = 0, val totalPages: Int = 0)

interface BookParser {
    suspend fun parseMetadata(filePath: String): BookMetadata
    suspend fun extractContent(filePath: String, chapterIndex: Int = 0): String
}

@Singleton
class BookParserFactory @Inject constructor() {
    fun getParser(format: BookFormat): BookParser = when (format) {
        BookFormat.EPUB -> EpubParser()
        BookFormat.PDF -> PdfParser()
        BookFormat.MOBI, BookFormat.AZW3 -> MobiParser()
        BookFormat.FB2 -> Fb2Parser()
        BookFormat.TXT -> TxtParser()
        BookFormat.HTML -> HtmlParser()
        BookFormat.CBZ, BookFormat.CBR -> CbzCbrParser()
    }
}
