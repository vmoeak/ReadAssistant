package com.readassistant.feature.reader.data.content

import javax.inject.Inject
import javax.inject.Singleton

/**
 * PDF content loader using MuPDF JNI.
 * Full integration requires MuPDF native library.
 */
@Singleton
class PdfContentLoader @Inject constructor() {

    suspend fun getPageCount(filePath: String): Int {
        // MuPDF: open document -> count pages
        return 0
    }

    suspend fun extractPageText(filePath: String, pageIndex: Int): String {
        // MuPDF: render page -> extract text
        return "PDF page $pageIndex text extraction requires MuPDF JNI."
    }
}
