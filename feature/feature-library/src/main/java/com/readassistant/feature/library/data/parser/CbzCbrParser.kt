package com.readassistant.feature.library.data.parser

class CbzCbrParser : BookParser {
    override suspend fun parseMetadata(filePath: String) = try {
        val pages = if (filePath.endsWith(".cbz", true)) {
            val z = java.util.zip.ZipFile(filePath); val c = z.entries().asSequence().filter { it.name.substringAfterLast(".").lowercase() in listOf("jpg","jpeg","png","gif","webp") }.count(); z.close(); c
        } else 0
        BookMetadata(title = filePath.substringAfterLast("/").substringBeforeLast("."), totalPages = pages)
    } catch (_: Exception) { BookMetadata(title = filePath.substringAfterLast("/").substringBeforeLast(".")) }
    override suspend fun extractContent(filePath: String, chapterIndex: Int) = "<p>Comic rendering uses ImageReader.</p>"
}
