package com.readassistant.feature.library.data.parser

class MobiParser : BookParser {
    override suspend fun parseMetadata(filePath: String) = try {
        val f = java.io.RandomAccessFile(filePath, "r"); val b = ByteArray(32); f.read(b); f.close()
        BookMetadata(title = String(b).trim('\u0000').ifBlank { filePath.substringAfterLast("/").substringBeforeLast(".") })
    } catch (_: Exception) { BookMetadata(title = filePath.substringAfterLast("/").substringBeforeLast(".")) }
    override suspend fun extractContent(filePath: String, chapterIndex: Int) = "<p>MOBI parsing requires binary parser.</p>"
}
