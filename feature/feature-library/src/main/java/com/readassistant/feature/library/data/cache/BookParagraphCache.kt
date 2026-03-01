package com.readassistant.feature.library.data.cache

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.BufferedWriter
import java.io.File
import java.util.LinkedHashMap

object BookParagraphCache {
    private const val CACHE_SCHEMA_VERSION = 20
    private const val CACHE_DIR_NAME = "book_paragraph_cache"
    private const val MAX_MEMORY_BOOKS = 6

    private val memoryCache = object : LinkedHashMap<String, CachedBookContent>(
        MAX_MEMORY_BOOKS,
        0.75f,
        true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedBookContent>?): Boolean {
            return size > MAX_MEMORY_BOOKS
        }
    }

    data class CachedLinkSpan(
        val start: Int,
        val end: Int,
        val href: String
    )

    data class CachedParagraph(
        val text: String,
        val isHeading: Boolean,
        val html: String = "",
        val imageSrc: String? = null,
        val plainText: String = "",
        val linkSpans: List<CachedLinkSpan> = emptyList(),
        val anchorIds: List<String> = emptyList()
    )

    data class CachedChapter(
        val title: String,
        val paragraphIndex: Int
    )

    data class CachedBookContent(
        val paragraphs: List<CachedParagraph>,
        val chapters: List<CachedChapter>
    )

    fun readMemoryCachedContent(
        bookId: Long,
        fileSize: Long,
        sourcePath: String
    ): CachedBookContent? {
        return memoryGet(buildCacheName(bookId, fileSize, sourcePath))
    }

    fun readCachedContent(
        context: Context,
        bookId: Long,
        fileSize: Long,
        sourcePath: String
    ): CachedBookContent? {
        val cacheFile = buildCacheFile(context, bookId, fileSize, sourcePath)
        val key = buildCacheName(bookId, fileSize, sourcePath)
        memoryGet(key)?.let { return it }
        if (!cacheFile.exists()) return null
        val raw = runCatching { cacheFile.readText(Charsets.UTF_8) }.getOrNull() ?: return null
        val parsed = parse(raw) ?: return null
        memoryPut(key, parsed)
        return parsed
    }

    fun writeCachedContent(
        context: Context,
        bookId: Long,
        fileSize: Long,
        sourcePath: String,
        content: CachedBookContent
    ) {
        if (content.paragraphs.isEmpty()) return
        val cacheFile = buildCacheFile(context, bookId, fileSize, sourcePath)
        memoryPut(buildCacheName(bookId, fileSize, sourcePath), content)
        runCatching {
            cacheFile.outputStream().bufferedWriter(Charsets.UTF_8).use { writer ->
                writeSerialized(writer, content)
            }
        }.onFailure { error ->
            Log.w("BookParagraphCache", "write cache failed: ${cacheFile.name}", error)
        }
    }

    fun buildFromHtml(html: String): CachedBookContent {
        val doc = Jsoup.parse(html)
        doc.select("script,style,iframe,noscript").remove()
        val body = doc.body()

        val paragraphs = mutableListOf<CachedParagraph>()
        val chapters = mutableListOf<CachedChapter>()
        val blockSelector = "img,image,h1,h2,h3,h4,h5,h6,p,li,blockquote,pre,figcaption,table,hr,div"
        val nestedBlockSelector = "img,image,h1,h2,h3,h4,h5,h6,p,li,blockquote,pre,table,figcaption,hr"
        val ancestorBlockTags = setOf("h1", "h2", "h3", "h4", "h5", "h6", "p", "li", "blockquote", "pre", "table", "figcaption", "hr")

        body.select(blockSelector).forEach { element ->
            val tag = element.tagName().lowercase()
            val isImageTag = tag == "img" || tag == "image"

            if (!isImageTag) {
                if (tag == "div" && element.selectFirst(nestedBlockSelector) != null) {
                    return@forEach
                }
                val hasBlockAncestor = element.parents().any { parent ->
                    parent != body && ancestorBlockTags.contains(parent.tagName().lowercase())
                }
                if (hasBlockAncestor) return@forEach
            }

            if (isImageTag) {
                val src = extractImageSource(element)
                if (src.isBlank()) return@forEach
                val alt = listOf(
                    element.attr("alt"),
                    element.attr("title"),
                    element.attr("aria-label")
                )
                    .firstOrNull { it.isNotBlank() }
                    .orEmpty()
                    .replace(Regex("\\s+"), " ")
                    .trim()
                val anchorHtml = buildAnchorMarkerHtml(element = element, body = body)
                val imgAnchorIds = collectAllAnchorIds(element, body)
                paragraphs += CachedParagraph(
                    text = alt,
                    isHeading = false,
                    html = anchorHtml,
                    imageSrc = src,
                    plainText = alt,
                    linkSpans = emptyList(),
                    anchorIds = imgAnchorIds
                )
                return@forEach
            }

            val text = element.text()
                .replace(Regex("\\s+"), " ")
                .trim()
            val htmlBlock = element
                .clone()
                .apply { select("img,image").remove() }
                .outerHtml()
                .trim()
            val inheritedAnchors = buildAncestorAnchorMarkerHtml(element = element, body = body)
            val htmlWithAnchors = (inheritedAnchors + htmlBlock).trim()
            if (text.isBlank() && htmlBlock.isBlank()) return@forEach
            if (text.isBlank() && isNonVisualHtmlBlock(htmlWithAnchors)) return@forEach
            val isHeading = tag.startsWith("h")
            val idx = paragraphs.size
            val (extractedText, extractedLinks) = extractPlainTextAndLinks(element)
            val elementAnchorIds = collectAllAnchorIds(element, body)
            paragraphs += CachedParagraph(
                text = text,
                isHeading = isHeading,
                html = htmlWithAnchors,
                plainText = extractedText,
                linkSpans = extractedLinks,
                anchorIds = elementAnchorIds
            )
            if (isHeading && text.isNotBlank()) {
                chapters += CachedChapter(
                    title = text.take(120),
                    paragraphIndex = idx
                )
            }
        }

        if (paragraphs.isEmpty()) {
            val fallback = body.text()
                .replace(Regex("\\s+"), " ")
                .trim()
            if (fallback.isNotBlank()) {
                paragraphs += CachedParagraph(
                    text = fallback,
                    isHeading = false,
                    html = body.html(),
                    plainText = fallback,
                    linkSpans = emptyList(),
                    anchorIds = emptyList()
                )
            }
        }

        return CachedBookContent(paragraphs = paragraphs, chapters = chapters)
    }

    private fun writeSerialized(writer: BufferedWriter, content: CachedBookContent) {
        writer.append("{\"schemaVersion\":")
        writer.append(CACHE_SCHEMA_VERSION.toString())
        writer.append(",\"paragraphs\":[")
        content.paragraphs.forEachIndexed { index, paragraph ->
            if (index > 0) writer.append(',')
            writer.append("{\"text\":")
            writer.append(JSONObject.quote(paragraph.text))
            writer.append(",\"isHeading\":")
            writer.append(paragraph.isHeading.toString())
            writer.append(",\"html\":")
            writer.append(JSONObject.quote(paragraph.html))
            writer.append(",\"imageSrc\":")
            writer.append(paragraph.imageSrc?.let(JSONObject::quote) ?: "null")
            writer.append(",\"plainText\":")
            writer.append(JSONObject.quote(paragraph.plainText))
            writer.append(",\"linkSpans\":[")
            paragraph.linkSpans.forEachIndexed { li, link ->
                if (li > 0) writer.append(',')
                writer.append("{\"s\":")
                writer.append(link.start.toString())
                writer.append(",\"e\":")
                writer.append(link.end.toString())
                writer.append(",\"h\":")
                writer.append(JSONObject.quote(link.href))
                writer.append('}')
            }
            writer.append("],\"anchorIds\":[")
            paragraph.anchorIds.forEachIndexed { ai, id ->
                if (ai > 0) writer.append(',')
                writer.append(JSONObject.quote(id))
            }
            writer.append("]}")
        }
        writer.append("],\"chapters\":[")
        content.chapters.forEachIndexed { index, chapter ->
            if (index > 0) writer.append(',')
            writer.append("{\"title\":")
            writer.append(JSONObject.quote(chapter.title))
            writer.append(",\"paragraphIndex\":")
            writer.append(chapter.paragraphIndex.toString())
            writer.append('}')
        }
        writer.append("]}")
    }

    private fun parse(raw: String): CachedBookContent? {
        return runCatching {
            val root = JSONObject(raw)
            val paragraphsJson = root.optJSONArray("paragraphs") ?: JSONArray()
            val chaptersJson = root.optJSONArray("chapters") ?: JSONArray()

            val paragraphs = mutableListOf<CachedParagraph>()
            for (i in 0 until paragraphsJson.length()) {
                val obj = paragraphsJson.optJSONObject(i) ?: continue
                val text = obj.optString("text").trim()
                val html = obj.optString("html").trim()
                val imageSrc = if (obj.isNull("imageSrc")) null
                    else obj.optString("imageSrc").trim().ifBlank { null }
                if (text.isBlank() && html.isBlank() && imageSrc.isNullOrBlank()) continue
                val plainText = obj.optString("plainText", "").trim()
                val linkSpansJson = obj.optJSONArray("linkSpans")
                val linkSpans = if (linkSpansJson != null) {
                    (0 until linkSpansJson.length()).mapNotNull { li ->
                        val lo = linkSpansJson.optJSONObject(li) ?: return@mapNotNull null
                        CachedLinkSpan(
                            start = lo.optInt("s", 0),
                            end = lo.optInt("e", 0),
                            href = lo.optString("h", "")
                        )
                    }.filter { it.start < it.end && it.href.isNotBlank() }
                } else emptyList()
                val anchorIdsJson = obj.optJSONArray("anchorIds")
                val anchorIds = if (anchorIdsJson != null) {
                    (0 until anchorIdsJson.length()).mapNotNull { ai ->
                        anchorIdsJson.optString(ai)?.trim()?.ifBlank { null }
                    }
                } else emptyList()
                paragraphs += CachedParagraph(
                    text = text,
                    isHeading = obj.optBoolean("isHeading", false),
                    html = html,
                    imageSrc = imageSrc,
                    plainText = plainText,
                    linkSpans = linkSpans,
                    anchorIds = anchorIds
                )
            }

            val chapters = mutableListOf<CachedChapter>()
            for (i in 0 until chaptersJson.length()) {
                val obj = chaptersJson.optJSONObject(i) ?: continue
                val title = obj.optString("title").trim()
                val paragraphIndex = obj.optInt("paragraphIndex", -1)
                if (title.isBlank() || paragraphIndex < 0) continue
                chapters += CachedChapter(title = title, paragraphIndex = paragraphIndex)
            }

            if (paragraphs.isEmpty()) null else CachedBookContent(paragraphs, chapters)
        }.getOrNull()
    }

    private fun buildCacheFile(
        context: Context,
        bookId: Long,
        fileSize: Long,
        sourcePath: String
    ): File {
        val cacheDir = File(context.cacheDir, CACHE_DIR_NAME).apply { mkdirs() }
        val cacheName = buildCacheName(bookId, fileSize, sourcePath)
        return File(cacheDir, cacheName)
    }

    private fun buildCacheName(bookId: Long, fileSize: Long, sourcePath: String): String {
        val source = File(sourcePath)
        val sourceMtime = runCatching { source.lastModified() }.getOrDefault(0L)
        return "v${CACHE_SCHEMA_VERSION}_book_${bookId}_${fileSize}_$sourceMtime.json"
    }

    fun isInMemoryCache(
        bookId: Long,
        fileSize: Long,
        sourcePath: String
    ): Boolean {
        val key = buildCacheName(bookId, fileSize, sourcePath)
        return synchronized(memoryCache) { memoryCache.containsKey(key) }
    }

    private fun memoryGet(key: String): CachedBookContent? = synchronized(memoryCache) {
        memoryCache[key]
    }

    private fun memoryPut(key: String, content: CachedBookContent) {
        synchronized(memoryCache) {
            memoryCache[key] = content
        }
    }

    private fun isNonVisualHtmlBlock(html: String): Boolean {
        val fragment = Jsoup.parseBodyFragment(html)
        if (fragment.select("[id],a[name]").isNotEmpty()) return false
        fragment.select("br,a,span").remove()
        val hasVisibleStructure = fragment.select("table,hr,blockquote,pre,ul,ol,li").isNotEmpty()
        if (hasVisibleStructure) return false
        val remainingText = fragment.body().text().trim()
        return remainingText.isBlank()
    }

    private fun buildAnchorMarkerHtml(element: Element, body: Element): String {
        val markers = linkedSetOf<String>()
        markers += collectAnchorMarkers(element)
        buildAncestorElements(element = element, body = body).forEach { ancestor ->
            markers += collectAnchorMarkers(ancestor)
        }
        return markers.joinToString(separator = "")
    }

    private fun buildAncestorAnchorMarkerHtml(element: Element, body: Element): String {
        val markers = linkedSetOf<String>()
        buildAncestorElements(element = element, body = body).forEach { ancestor ->
            markers += collectAnchorMarkers(ancestor)
        }
        return markers.joinToString(separator = "")
    }

    private fun buildAncestorElements(element: Element, body: Element): List<Element> {
        return element.parents()
            .takeWhile { it != body }
            .asReversed()
    }

    private fun collectAnchorMarkers(element: Element): List<String> {
        val markers = mutableListOf<String>()
        val id = element.id().trim()
        if (id.isNotBlank()) {
            markers += """<a id="${escapeAttribute(id)}"></a>"""
        }
        val name = element.attr("name").trim()
        if (name.isNotBlank()) {
            markers += """<a name="${escapeAttribute(name)}"></a>"""
        }
        return markers
    }

    private fun extractImageSource(element: Element): String {
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

    private fun extractPlainTextAndLinks(element: Element): Pair<String, List<CachedLinkSpan>> {
        val sb = StringBuilder()
        val links = mutableListOf<CachedLinkSpan>()
        walkElementForText(element, sb, links, null)
        val raw = sb.toString()
        val plainText = raw.replace(Regex("\\s+"), " ").trim()
        return plainText to remapCachedLinks(raw, plainText, links)
    }

    private fun walkElementForText(
        node: org.jsoup.nodes.Node,
        sb: StringBuilder,
        links: MutableList<CachedLinkSpan>,
        currentHref: String?
    ) {
        when (node) {
            is org.jsoup.nodes.TextNode -> {
                val text = node.wholeText
                if (text.isNotEmpty()) sb.append(text)
            }
            is Element -> {
                val tag = node.tagName().lowercase()
                if (tag == "img" || tag == "image") return
                val isLink = tag == "a" && node.hasAttr("href")
                val href = if (isLink) {
                    val h = node.attr("href").trim()
                    if (h.isNotBlank() && h.startsWith("#")) h else null
                } else null
                val linkStart = if (isLink && href != null) sb.length else -1
                for (child in node.childNodes()) {
                    walkElementForText(child, sb, links, href ?: currentHref)
                }
                if (isLink && href != null && linkStart >= 0 && sb.length > linkStart) {
                    links += CachedLinkSpan(start = linkStart, end = sb.length, href = href)
                }
            }
        }
    }

    private fun remapCachedLinks(
        raw: String,
        normalized: String,
        links: List<CachedLinkSpan>
    ): List<CachedLinkSpan> {
        if (links.isEmpty()) return emptyList()
        val rawToNorm = IntArray(raw.length + 1) { -1 }
        val leadingTrimmed = raw.length - raw.trimStart().length
        var ri = leadingTrimmed
        var ni = 0
        while (ri < raw.length && ni < normalized.length) {
            if (raw[ri] == normalized[ni]) {
                rawToNorm[ri] = ni
                ri++
                ni++
            } else if (raw[ri].isWhitespace()) {
                rawToNorm[ri] = ni
                ri++
            } else {
                ri++
            }
        }
        while (ri <= raw.length) {
            rawToNorm[ri] = ni.coerceAtMost(normalized.length)
            ri++
        }
        return links.mapNotNull { link ->
            val newStart = rawToNorm.getOrNull(link.start) ?: return@mapNotNull null
            val newEnd = rawToNorm.getOrNull(link.end)
                ?: rawToNorm.getOrNull(link.end.coerceAtMost(raw.length))
                ?: return@mapNotNull null
            if (newStart >= newEnd || newStart >= normalized.length) return@mapNotNull null
            CachedLinkSpan(
                start = newStart,
                end = newEnd.coerceAtMost(normalized.length),
                href = link.href
            )
        }
    }

    private fun collectAllAnchorIds(element: Element, body: Element): List<String> {
        val ids = mutableListOf<String>()
        val id = element.id().trim()
        if (id.isNotBlank()) ids += id
        val name = element.attr("name").trim()
        if (name.isNotBlank()) ids += name
        element.parents().takeWhile { it != body }.forEach { ancestor ->
            val aId = ancestor.id().trim()
            if (aId.isNotBlank()) ids += aId
            val aName = ancestor.attr("name").trim()
            if (aName.isNotBlank()) ids += aName
        }
        return ids
    }

    private fun escapeAttribute(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
}
