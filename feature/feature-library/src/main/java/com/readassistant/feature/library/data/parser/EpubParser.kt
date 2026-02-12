package com.readassistant.feature.library.data.parser

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import java.net.URLDecoder
import java.util.Base64
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

            val manifest = parseManifest(opfText)
            val spineRefs = parseSpineRefs(opfText)
            val orderedContentPaths = spineRefs
                .mapNotNull { idref ->
                    val href = manifest[idref] ?: return@mapNotNull null
                    val resolved = resolveZipPath(opfBase, href)
                    when {
                        zip.getEntry(resolved) != null -> resolved
                        zip.getEntry(href) != null -> href
                        else -> null
                    }
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
            val entryPrefix = targetEntries.mapIndexed { index, path ->
                path to "s$index"
            }.toMap()

            val htmlParts = targetEntries.mapNotNull { entryPath ->
                val entry = zip.getEntry(entryPath) ?: return@mapNotNull null
                val raw = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                val doc = Jsoup.parse(raw)
                doc.select("script,style,iframe").remove()
                rewriteDocumentAnchors(
                    doc = doc,
                    currentEntryPath = entryPath,
                    currentPrefix = entryPrefix[entryPath].orEmpty(),
                    entryPrefix = entryPrefix
                )
                val baseDir = entryPath.substringBeforeLast("/", missingDelimiterValue = "")
                doc.select("img[src]").forEach { img ->
                    val src = img.attr("src").trim()
                    if (src.isBlank()) return@forEach
                    if (src.startsWith("data:", ignoreCase = true)) return@forEach
                    if (src.startsWith("http://", ignoreCase = true) || src.startsWith("https://", ignoreCase = true)) return@forEach
                    val resolved = resolveZipPath(baseDir, src)
                    val imageEntry = zip.getEntry(resolved) ?: return@forEach
                    val bytes = runCatching { zip.getInputStream(imageEntry).use { it.readBytes() } }.getOrNull()
                        ?: return@forEach
                    val mime = guessMimeType(imageEntry.name)
                    val b64 = Base64.getEncoder().encodeToString(bytes)
                    img.attr("src", "data:$mime;base64,$b64")
                }

                val body = doc.body().html().trim()
                if (body.isBlank()) null else "<section>$body</section>"
            }

            zip.close()
            if (htmlParts.isEmpty()) "<p>No readable EPUB content found.</p>" else htmlParts.joinToString("\n")
        } catch (_: Exception) {
            "<p>Error reading EPUB.</p>"
        }
    }

    private fun rewriteDocumentAnchors(
        doc: Document,
        currentEntryPath: String,
        currentPrefix: String,
        entryPrefix: Map<String, String>
    ) {
        if (currentPrefix.isBlank()) return
        doc.select("[id]").forEach { element ->
            val id = element.id().trim()
            if (id.isNotBlank()) {
                element.attr("id", "${currentPrefix}__${id}")
            }
        }
        doc.select("a[name]").forEach { element ->
            val name = element.attr("name").trim()
            if (name.isNotBlank()) {
                element.attr("name", "${currentPrefix}__${name}")
            }
        }
        val baseDir = currentEntryPath.substringBeforeLast("/", missingDelimiterValue = "")
        doc.select("a[href]").forEach { anchor ->
            val href = anchor.attr("href").trim()
            if (href.isBlank()) return@forEach
            if (
                href.startsWith("http://", ignoreCase = true) ||
                href.startsWith("https://", ignoreCase = true) ||
                href.startsWith("mailto:", ignoreCase = true) ||
                href.startsWith("javascript:", ignoreCase = true)
            ) {
                return@forEach
            }
            if (href.startsWith("#")) {
                val id = href.removePrefix("#").trim()
                if (id.isNotBlank()) {
                    anchor.attr("href", "#${currentPrefix}__${id}")
                }
                return@forEach
            }
            if (!href.contains("#")) return@forEach
            val targetPathRaw = href.substringBefore('#')
            val targetId = href.substringAfter('#').trim()
            if (targetId.isBlank()) return@forEach
            val resolvedTarget = resolveZipPath(baseDir, targetPathRaw)
            val targetPrefix = entryPrefix[resolvedTarget] ?: return@forEach
            anchor.attr("href", "#${targetPrefix}__${targetId}")
        }
    }

    private fun resolveZipPath(baseDir: String, rawRef: String): String {
        val ref = rawRef.substringBefore('#').substringBefore('?').trim()
        val decoded = runCatching { URLDecoder.decode(ref, Charsets.UTF_8.name()) }.getOrDefault(ref)
        if (decoded.startsWith("/")) return decoded.trimStart('/')
        val baseParts = if (baseDir.isBlank()) mutableListOf() else baseDir.split('/').toMutableList()
        decoded.split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (baseParts.isNotEmpty()) baseParts.removeAt(baseParts.lastIndex)
                else -> baseParts += part
            }
        }
        return baseParts.joinToString("/")
    }

    private fun parseManifest(opfText: String): Map<String, String> {
        val doc = Jsoup.parse(opfText, "", Parser.xmlParser())
        val map = linkedMapOf<String, String>()
        doc.select("manifest > item").forEach { item ->
            val id = item.attr("id").trim()
            val href = item.attr("href").trim()
            if (id.isNotBlank() && href.isNotBlank()) {
                map[id] = href
            }
        }
        if (map.isNotEmpty()) return map

        Regex("""<item\b([^>]*)/?>""", RegexOption.IGNORE_CASE)
            .findAll(opfText)
            .forEach { match ->
                val attrs = match.groupValues[1]
                val id = Regex("""\bid\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                    .find(attrs)
                    ?.groupValues
                    ?.get(1)
                    ?.trim()
                    .orEmpty()
                val href = Regex("""\bhref\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                    .find(attrs)
                    ?.groupValues
                    ?.get(1)
                    ?.trim()
                    .orEmpty()
                if (id.isNotBlank() && href.isNotBlank()) {
                    map[id] = href
                }
            }
        return map
    }

    private fun parseSpineRefs(opfText: String): List<String> {
        val doc = Jsoup.parse(opfText, "", Parser.xmlParser())
        val refs = doc.select("spine > itemref")
            .mapNotNull { itemRef ->
                itemRef.attr("idref")
                    .trim()
                    .ifBlank { null }
            }
        if (refs.isNotEmpty()) return refs

        return Regex("""<itemref\b([^>]*)/?>""", RegexOption.IGNORE_CASE)
            .findAll(opfText)
            .mapNotNull { match ->
                Regex("""\bidref\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                    .find(match.groupValues[1])
                    ?.groupValues
                    ?.get(1)
                    ?.trim()
                    ?.ifBlank { null }
            }
            .toList()
    }

    private fun guessMimeType(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".gif") -> "image/gif"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".svg") -> "image/svg+xml"
            else -> "image/*"
        }
    }
}
