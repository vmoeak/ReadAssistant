package com.readassistant.feature.library.data.parser

import org.jsoup.Jsoup
import java.util.zip.ZipFile

class EpubParser : BookParser {
    override suspend fun parseMetadata(filePath: String): BookMetadata = try {
        val zip = ZipFile(filePath)
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

    override suspend fun extractContent(filePath: String, chapterIndex: Int): String {
        return try {
            val zip = ZipFile(filePath)

            val containerPath = "META-INF/container.xml"
            val containerEntry = zip.getEntry(containerPath)
            val rootOpfPath = if (containerEntry != null) {
                val containerXml = zip.getInputStream(containerEntry).bufferedReader().use { it.readText() }
                Regex("""full-path\s*=\s*["']([^"']+)["']""").find(containerXml)?.groupValues?.get(1)
            } else null

            val opfPath = rootOpfPath
                ?: zip.entries().asSequence().find { it.name.endsWith(".opf", ignoreCase = true) }?.name

            if (opfPath == null) {
                zip.close()
                return "<p>EPUB metadata not found.</p>"
            }

            val opfText = zip.getInputStream(zip.getEntry(opfPath)).bufferedReader().use { it.readText() }
            val opfBase = opfPath.substringBeforeLast("/", missingDelimiterValue = "")

            val manifest = mutableMapOf<String, String>()
            Regex("""<item\b[^>]*\bid\s*=\s*["']([^"']+)["'][^>]*\bhref\s*=\s*["']([^"']+)["'][^>]*>""")
                .findAll(opfText)
                .forEach { m ->
                    manifest[m.groupValues[1]] = m.groupValues[2]
                }

            val orderedContentPaths = Regex("""<itemref\b[^>]*\bidref\s*=\s*["']([^"']+)["'][^>]*>""")
                .findAll(opfText)
                .mapNotNull { m ->
                    val href = manifest[m.groupValues[1]] ?: return@mapNotNull null
                    if (opfBase.isBlank()) href else "$opfBase/$href"
                }
                .toList()

            val targetEntries = if (orderedContentPaths.isNotEmpty()) {
                orderedContentPaths
            } else {
                zip.entries().asSequence()
                    .map { it.name }
                    .filter {
                        it.endsWith(".xhtml", true) || it.endsWith(".html", true) || it.endsWith(".htm", true)
                    }
                    .sorted()
                    .toList()
            }

            val htmlParts = targetEntries.mapNotNull { entryPath ->
                val entry = zip.getEntry(entryPath) ?: return@mapNotNull null
                val raw = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                val body = Jsoup.parse(raw).apply { select("script,style,iframe").remove() }.body()?.html().orEmpty().trim()
                if (body.isBlank()) null else "<section>$body</section>"
            }

            zip.close()
            if (htmlParts.isEmpty()) "<p>No readable EPUB content found.</p>" else htmlParts.joinToString("\n")
        } catch (_: Exception) {
            "<p>Error reading EPUB.</p>"
        }
    }
}
