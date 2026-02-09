package com.readassistant.feature.library.data.parser

import org.jsoup.Jsoup

class HtmlParser : BookParser {
    override suspend fun parseMetadata(filePath: String) = try {
        val doc = Jsoup.parse(java.io.File(filePath), "UTF-8")
        BookMetadata(title = doc.title().ifBlank { filePath.substringAfterLast("/").substringBeforeLast(".") })
    } catch (_: Exception) { BookMetadata(title = filePath.substringAfterLast("/").substringBeforeLast(".")) }
    override suspend fun extractContent(filePath: String, chapterIndex: Int) = try {
        val doc = Jsoup.parse(java.io.File(filePath), "UTF-8"); doc.select("script, style, iframe").remove(); doc.body()?.html() ?: ""
    } catch (_: Exception) { "<p>Error reading HTML.</p>" }
}
