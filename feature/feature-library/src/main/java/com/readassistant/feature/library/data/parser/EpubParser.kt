package com.readassistant.feature.library.data.parser

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.File
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class EpubParser : BookParser {
    override suspend fun parseMetadata(filePath: String): BookMetadata = try {
        ZipFile(filePath).use { zip ->
            val entryLookup = buildZipEntryLookup(zip)
            var title = filePath.substringAfterLast("/").substringBeforeLast(".")
            var author = ""
            var coverPath: String? = null
            val opfEntry = zip.entries().asSequence().firstOrNull { it.name.endsWith(".opf", ignoreCase = true) }
                ?: resolveZipEntry(entryLookup, zip, "content.opf")
            if (opfEntry != null) {
                val opfText = zip.getInputStream(opfEntry).bufferedReader().use { it.readText() }
                Regex("<dc:title[^>]*>([^<]+)</dc:title>").find(opfText)?.let { title = it.groupValues[1] }
                Regex("<dc:creator[^>]*>([^<]+)</dc:creator>").find(opfText)?.let { author = it.groupValues[1] }

                val manifest = parseManifest(opfText)
                val opfBase = opfEntry.name.substringBeforeLast("/", missingDelimiterValue = "")
                val coverHref = extractCoverHref(opfText, manifest)
                if (!coverHref.isNullOrBlank()) {
                    coverPath = resolveImageSource(
                        zip = zip,
                        entryLookup = entryLookup,
                        baseDir = opfBase,
                        rawRef = coverHref,
                        epubFilePath = filePath,
                        outputDir = null
                    )
                }

                if (coverPath.isNullOrBlank()) {
                    val firstSpinePath = parseSpineRefs(opfText)
                        .asSequence()
                        .mapNotNull { idref ->
                            val href = manifest[idref] ?: return@mapNotNull null
                            val resolved = resolveZipPath(opfBase, href)
                            resolveZipEntryName(entryLookup, resolved)
                                ?: resolveZipEntryName(entryLookup, href)
                        }
                        .firstOrNull()
                    if (!firstSpinePath.isNullOrBlank()) {
                        val firstDocEntry = resolveZipEntry(entryLookup, zip, firstSpinePath)
                        if (firstDocEntry != null) {
                            val raw = zip.getInputStream(firstDocEntry).bufferedReader().use { it.readText() }
                            val doc = Jsoup.parse(raw)
                            val firstImageRef = doc.select("img, image")
                                .asSequence()
                                .map { extractImageRef(it) }
                                .firstOrNull { it.isNotBlank() }
                            if (!firstImageRef.isNullOrBlank()) {
                                val baseDir = firstDocEntry.name.substringBeforeLast("/", missingDelimiterValue = "")
                                coverPath = resolveImageSource(
                                    zip = zip,
                                    entryLookup = entryLookup,
                                    baseDir = baseDir,
                                    rawRef = firstImageRef,
                                    epubFilePath = filePath,
                                    outputDir = null
                                )
                            }
                        }
                    }
                }
            }
            BookMetadata(title = title, author = author, coverPath = coverPath)
        }
    } catch (_: Throwable) { BookMetadata(title = filePath.substringAfterLast("/").substringBeforeLast(".")) }

    override suspend fun extractContent(filePath: String, chapterIndex: Int, outputDir: String?): String {
        return try {
            val zip = ZipFile(filePath)
            val entryLookup = buildZipEntryLookup(zip)

            val containerPath = "META-INF/container.xml"
            val containerEntry = resolveZipEntry(entryLookup, zip, containerPath)
            val rootOpfPath = if (containerEntry != null) {
                val containerXml = zip.getInputStream(containerEntry).bufferedReader().use { it.readText() }
                Regex("""full-path\s*=\s*["']([^"']+)["']""").find(containerXml)?.groupValues?.get(1)
            } else null

            val opfEntry = rootOpfPath
                ?.let { resolveZipEntry(entryLookup, zip, it) }
                ?: zip.entries().asSequence().find { it.name.endsWith(".opf", ignoreCase = true) }

            if (opfEntry == null) {
                zip.close()
                return "<p>EPUB metadata not found.</p>"
            }

            val opfPath = opfEntry.name
            val opfText = zip.getInputStream(opfEntry).bufferedReader().use { it.readText() }
            val opfBase = opfPath.substringBeforeLast("/", missingDelimiterValue = "")

            val manifest = parseManifest(opfText)
            val spineRefs = parseSpineRefs(opfText)
            val orderedContentPaths = spineRefs
                .mapNotNull { idref ->
                    val href = manifest[idref] ?: return@mapNotNull null
                    val resolved = resolveZipPath(opfBase, href)
                    resolveZipEntryName(entryLookup, resolved)
                        ?: resolveZipEntryName(entryLookup, href)
                }
                .distinct()
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
            val entryPrefix = mutableMapOf<String, String>()
            targetEntries.forEachIndexed { index, path ->
                val prefix = "s$index"
                entryPrefix[path] = prefix
                entryPrefix[normalizeZipEntryKey(path)] = prefix
            }

            val htmlParts = targetEntries.mapNotNull { entryPath ->
                val entry = resolveZipEntry(entryLookup, zip, entryPath) ?: return@mapNotNull null
                val raw = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                val doc = Jsoup.parse(raw)
                doc.select("script,style,iframe").remove()
                rewriteDocumentAnchors(
                    doc = doc,
                    currentEntryPath = entry.name,
                    currentPrefix = entryPrefix[entry.name].orEmpty(),
                    entryPrefix = entryPrefix
                )
                val baseDir = entry.name.substringBeforeLast("/", missingDelimiterValue = "")

                doc.select("img").forEach { img ->
                    val src = extractImageRef(img)
                    val resolvedSrc = resolveImageSource(
                        zip = zip,
                        entryLookup = entryLookup,
                        baseDir = baseDir,
                        rawRef = src,
                        epubFilePath = filePath,
                        outputDir = outputDir
                    ) ?: return@forEach
                    img.attr("src", resolvedSrc)
                    img.removeAttr("srcset")
                }
                doc.select("image").forEach { imageNode ->
                    val src = extractImageRef(imageNode)
                    val resolvedSrc = resolveImageSource(
                        zip = zip,
                        entryLookup = entryLookup,
                        baseDir = baseDir,
                        rawRef = src,
                        epubFilePath = filePath,
                        outputDir = outputDir
                    ) ?: return@forEach
                    val replacement = Element("img")
                        .attr("src", resolvedSrc)
                    val alt = imageNode.attr("aria-label").trim()
                    if (alt.isNotBlank()) {
                        replacement.attr("alt", alt)
                    }
                    imageNode.replaceWith(replacement)
                }

                val prefix = entryPrefix[entry.name].orEmpty()
                val body = doc.body().html().trim()
                if (body.isBlank()) null
                else if (prefix.isNotBlank()) """<section id="$prefix">$body</section>"""
                else "<section>$body</section>"
            }

            zip.close()
            if (htmlParts.isEmpty()) "<p>No readable EPUB content found.</p>" else htmlParts.joinToString("\n")
        } catch (_: Throwable) {
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
            if (!href.contains("#")) {
                // File-level link without fragment — rewrite to section-level anchor
                val resolvedTarget = resolveZipPath(baseDir, href)
                val targetPrefix = entryPrefix[resolvedTarget]
                    ?: entryPrefix[normalizeZipEntryKey(resolvedTarget)]
                    ?: return@forEach
                anchor.attr("href", "#$targetPrefix")
                return@forEach
            }
            val targetPathRaw = href.substringBefore('#')
            val targetId = href.substringAfter('#').trim()
            if (targetId.isBlank()) return@forEach
            val resolvedTarget = resolveZipPath(baseDir, targetPathRaw)
            val targetPrefix = entryPrefix[resolvedTarget]
                ?: entryPrefix[normalizeZipEntryKey(resolvedTarget)]
                ?: return@forEach
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

    private fun extractCoverHref(opfText: String, manifest: Map<String, String>): String? {
        val doc = Jsoup.parse(opfText, "", Parser.xmlParser())
        val byProperty = doc.select("manifest > item[properties~=cover-image]")
            .firstOrNull()
            ?.attr("href")
            ?.trim()
            ?.ifBlank { null }
        if (byProperty != null) return byProperty

        val coverId = doc.select("metadata > meta[name=cover]")
            .firstOrNull()
            ?.attr("content")
            ?.trim()
            ?.ifBlank { null }
            ?: Regex("""<meta\b[^>]*name\s*=\s*["']cover["'][^>]*content\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                .find(opfText)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
                ?.ifBlank { null }
        if (coverId != null) {
            val href = manifest[coverId]?.trim()
            if (!href.isNullOrBlank()) return href
        }

        return manifest.entries
            .firstOrNull { (id, href) ->
                id.contains("cover", ignoreCase = true) ||
                    href.contains("cover", ignoreCase = true)
            }
            ?.value
            ?.trim()
            ?.ifBlank { null }
    }

    private fun extractImageRef(element: Element): String {
        val candidates = listOf(
            element.attr("src"),
            element.attr("data-src"),
            element.attr("data-original"),
            element.attr("data-lazy-src"),
            extractFirstSrcFromSrcSet(element.attr("srcset")),
            element.attr("href"),
            element.attr("xlink:href")
        )
        return candidates
            .asSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
    }

    private fun extractFirstSrcFromSrcSet(srcSet: String): String {
        if (srcSet.isBlank()) return ""
        return srcSet.substringBefore(',')
            .trim()
            .substringBefore(' ')
            .trim()
    }

    private fun resolveImageSource(
        zip: ZipFile,
        entryLookup: Map<String, String>,
        baseDir: String,
        rawRef: String,
        epubFilePath: String,
        outputDir: String?
    ): String? {
        val src = rawRef.trim().removePrefix("./")
        if (src.isBlank()) return null
        if (src.startsWith("data:", ignoreCase = true)) return src
        if (src.startsWith("http://", ignoreCase = true) || src.startsWith("https://", ignoreCase = true)) {
            return src
        }

        val fileName = src.substringAfterLast('/')
        val resolved = resolveZipPath(baseDir, src)

        val imageEntry = resolveZipEntry(entryLookup, zip, resolved)
            ?: resolveZipEntry(entryLookup, zip, src)
            ?: zip.entries().asSequence().find { it.name.endsWith("/$fileName", ignoreCase = true) || it.name.equals(fileName, ignoreCase = true) }
            ?: zip.entries().asSequence().find { it.name.contains(fileName, ignoreCase = true) && !it.isDirectory }

        if (imageEntry == null) return null

        // Fast path: check if image was already extracted to disk (avoids re-reading zip bytes)
        val uncompressedSize = imageEntry.size
        if (uncompressedSize > 0) {
            val extension = imageEntry.name.substringAfterLast('.', missingDelimiterValue = "").lowercase().trim()
            val digest = sha1("${imageEntry.name}:$uncompressedSize")
            val finalName = if (extension.isNotBlank()) "$digest.$extension" else digest
            val dir = if (outputDir != null) {
                File(outputDir, ".epub_images")
            } else {
                File(epubFilePath).parentFile?.let { File(it, ".epub_images") }
            }
            if (dir != null) {
                val existing = File(dir, finalName)
                if (existing.exists() && existing.length() == uncompressedSize) {
                    return existing.absolutePath
                }
            }
        }

        // Slow path: read bytes from zip and write to disk
        val bytes = runCatching { zip.getInputStream(imageEntry).use { it.readBytes() } }.getOrNull()
            ?: return null

        return materializeImageFile(
            epubFilePath = epubFilePath,
            entryName = imageEntry.name,
            bytes = bytes,
            outputDir = outputDir
        )
    }

    private fun materializeImageFile(
        epubFilePath: String,
        entryName: String,
        bytes: ByteArray,
        outputDir: String?
    ): String? {
        if (bytes.isEmpty()) return null
        return runCatching {
            val dir = if (outputDir != null) {
                File(outputDir, ".epub_images").apply { mkdirs() }
            } else {
                val parent = File(epubFilePath).parentFile ?: return null
                File(parent, ".epub_images").apply { mkdirs() }
            }
            val extension = entryName.substringAfterLast('.', missingDelimiterValue = "").lowercase().trim()
            val digest = sha1("$entryName:${bytes.size}")
            val finalName = if (extension.isNotBlank()) "$digest.$extension" else digest
            val output = File(dir, finalName)
            
            if (!output.exists() || output.length() != bytes.size.toLong()) {
                val tempFile = File(dir, "$finalName.tmp")
                tempFile.writeBytes(bytes)
                tempFile.renameTo(output)
            }
            output.absolutePath
        }.getOrNull()
    }

    private fun buildZipEntryLookup(zip: ZipFile): Map<String, String> {
        val lookup = linkedMapOf<String, String>()
        zip.entries().asSequence().forEach { entry ->
            lookup.putIfAbsent(normalizeZipEntryKey(entry.name), entry.name)
        }
        return lookup
    }

    private fun resolveZipEntryName(entryLookup: Map<String, String>, rawPath: String): String? {
        val key = normalizeZipEntryKey(rawPath)
        return entryLookup[key]
    }

    private fun resolveZipEntry(entryLookup: Map<String, String>, zip: ZipFile, rawPath: String): ZipEntry? {
        val name = resolveZipEntryName(entryLookup, rawPath) ?: return null
        return zip.getEntry(name)
    }

    private fun normalizeZipEntryKey(path: String): String {
        val trimmed = path.trim().replace('\\', '/').trimStart('/')
        if (trimmed.isBlank()) return ""
        val decoded = runCatching { URLDecoder.decode(trimmed, Charsets.UTF_8.name()) }.getOrDefault(trimmed)
        val normalized = decoded
            .replace('\\', '/')
            .split('/')
            .fold(mutableListOf<String>()) { acc, part ->
                when (part) {
                    "", "." -> Unit
                    ".." -> if (acc.isNotEmpty()) acc.removeAt(acc.lastIndex)
                    else -> acc += part
                }
                acc
            }
            .joinToString("/")
        return normalized.lowercase()
    }

    private fun sha1(value: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        val bytes = md.digest(value.toByteArray(Charsets.UTF_8))
        return bytes.joinToString(separator = "") { "%02x".format(it) }
    }
}
