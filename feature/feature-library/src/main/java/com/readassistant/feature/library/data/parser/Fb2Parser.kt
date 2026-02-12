package com.readassistant.feature.library.data.parser

class Fb2Parser : BookParser {
    override suspend fun parseMetadata(filePath: String) = try {
        val text = java.io.File(filePath).readText()
        val title = Regex("<book-title>([^<]+)</book-title>").find(text)?.groupValues?.get(1) ?: filePath.substringAfterLast("/").substringBeforeLast(".")
        val author = Regex("<first-name>([^<]+)</first-name>").find(text)?.groupValues?.get(1) ?: ""
        BookMetadata(title = title, author = author)
    } catch (_: Exception) { BookMetadata(title = filePath.substringAfterLast("/").substringBeforeLast(".")) }
    override suspend fun extractContent(filePath: String, chapterIndex: Int, outputDir: String?) = try {
        val text = java.io.File(filePath).readText()
        Regex("<body[^>]*>(.*?)</body>", RegexOption.DOT_MATCHES_ALL).find(text)?.groupValues?.get(1)
            ?.replace(Regex("<section[^>]*>"), "<div>")?.replace("</section>", "</div>")
            ?.replace("<title>", "<h2>")?.replace("</title>", "</h2>") ?: "<p>Error</p>"
    } catch (_: Exception) { "<p>Error reading FB2.</p>" }
}
