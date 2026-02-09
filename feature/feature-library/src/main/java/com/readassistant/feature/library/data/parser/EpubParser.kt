package com.readassistant.feature.library.data.parser

class EpubParser : BookParser {
    override suspend fun parseMetadata(filePath: String): BookMetadata = try {
        val zip = java.util.zip.ZipFile(filePath)
        var title = filePath.substringAfterLast("/").substringBeforeLast(".")
        var author = ""
        val opf = zip.entries().asSequence().find { it.name.endsWith(".opf") }
        if (opf != null) {
            val c = zip.getInputStream(opf).bufferedReader().readText()
            Regex("<dc:title[^>]*>([^<]+)</dc:title>").find(c)?.let { title = it.groupValues[1] }
            Regex("<dc:creator[^>]*>([^<]+)</dc:creator>").find(c)?.let { author = it.groupValues[1] }
        }
        zip.close(); BookMetadata(title = title, author = author)
    } catch (_: Exception) { BookMetadata(title = filePath.substringAfterLast("/").substringBeforeLast(".")) }

    override suspend fun extractContent(filePath: String, chapterIndex: Int) = "<p>EPUB rendering requires Readium integration.</p>"
}
