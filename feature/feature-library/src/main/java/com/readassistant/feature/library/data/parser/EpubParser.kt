package com.readassistant.feature.library.data.parser

import org.jsoup.Jsoup
import java.util.Base64
import java.util.Locale
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
                val doc = Jsoup.parse(raw)
                doc.select("script,style,iframe").remove()

                val entryDir = entryPath.substringBeforeLast("/", missingDelimiterValue = "")
                doc.select("img[src]").forEach { img ->
                    val src = img.attr("src").trim()
                    if (src.isBlank() || src.startsWith("data:", ignoreCase = true)) return@forEach
                    val normalizedPath = resolveEpubPath(entryDir, src) ?: return@forEach
                    val imageEntry = zip.getEntry(normalizedPath) ?: return@forEach
                    val bytes = zip.getInputStream(imageEntry).use { it.readBytes() }
                    if (bytes.isEmpty()) return@forEach
                    val mimeType = detectMimeType(normalizedPath)
                    val base64 = Base64.getEncoder().encodeToString(bytes)
                    img.attr("src", "data:$mimeType;base64,$base64")
                }

                val body = doc.body()?.html().orEmpty().trim()
                if (body.isBlank()) null else "<section>$body</section>"
            }

            zip.close()
            if (htmlParts.isEmpty()) "<p>No readable EPUB content found.</p>" else htmlParts.joinToString("\n")
        } catch (_: Exception) {
            "<p>Error reading EPUB.</p>"
        }
    }

    private fun resolveEpubPath(currentDir: String, relativePath: String): String? {
        if (relativePath.startsWith("http://", true) || relativePath.startsWith("https://", true)) return null
        if (relativePath.startsWith("/")) return relativePath.trimStart('/')
        val raw = if (currentDir.isBlank()) relativePath else "$currentDir/$relativePath"
        val parts = mutableListOf<String>()
        raw.split('/').forEach { segment ->
            when {
                segment.isBlank() || segment == "." -> Unit
                segment == ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                else -> parts.add(segment)
            }
        }
        return if (parts.isEmpty()) null else parts.joinToString("/")
    }

    private fun detectMimeType(path: String): String {
        return when (path.substringAfterLast('.', "").lowercase(Locale.US)) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            "svg" -> "image/svg+xml"
            "avif" -> "image/avif"
            else -> "application/octet-stream"
        }
    }
}
