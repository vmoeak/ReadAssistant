package com.readassistant.feature.library.data.parser

class TxtParser : BookParser {
    override suspend fun parseMetadata(filePath: String) = BookMetadata(title = filePath.substringAfterLast("/").substringBeforeLast("."))
    override suspend fun extractContent(filePath: String, chapterIndex: Int, outputDir: String?): String {
        val text = java.io.File(filePath).readText(Charsets.UTF_8)
        return text.split(Regex("\n\\s*\n")).joinToString("\n") { "<p>${it.trim().replace("\n", "<br>")}</p>" }
    }
}
