package com.readassistant.feature.library.data.parser

class PdfParser : BookParser {
    override suspend fun parseMetadata(filePath: String) = BookMetadata(title = filePath.substringAfterLast("/").substringBeforeLast("."))
    override suspend fun extractContent(filePath: String, chapterIndex: Int, outputDir: String?) = "<p>PDF rendering requires MuPDF.</p>"
}
