package com.readassistant.feature.library.data.cache

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.File
import java.util.LinkedHashMap

object BookParagraphCache {
    private const val CACHE_SCHEMA_VERSION = 6
    private const val CACHE_DIR_NAME = "book_paragraph_cache"
    private const val MAX_MEMORY_BOOKS = 4

    private val memoryCache = object : LinkedHashMap<String, CachedBookContent>(
        MAX_MEMORY_BOOKS,
        0.75f,
        true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedBookContent>?): Boolean {
            return size > MAX_MEMORY_BOOKS
        }
    }

    data class CachedParagraph(
        val text: String,
        val isHeading: Boolean,
        val html: String = "",
        val imageSrc: String? = null
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
        runCatching { cacheFile.writeText(serialize(content), Charsets.UTF_8) }
    }

    fun buildFromHtml(html: String): CachedBookContent {
        val doc = Jsoup.parse(html)
        doc.select("script,style,iframe,noscript").remove()
        val body = doc.body()

        val paragraphs = mutableListOf<CachedParagraph>()
        val chapters = mutableListOf<CachedChapter>()
        val blockSelector = "img,h1,h2,h3,h4,h5,h6,p,li,blockquote,pre,figcaption,table,hr,div"
        val nestedBlockSelector = "h1,h2,h3,h4,h5,h6,p,li,blockquote,pre,table,figcaption,hr"
        val ancestorBlockTags = setOf("h1", "h2", "h3", "h4", "h5", "h6", "p", "li", "blockquote", "pre", "table", "figcaption", "hr")

        body.select(blockSelector).forEach { element ->
            val tag = element.tagName().lowercase()
            if (tag != "img") {
                if (tag == "div" && element.selectFirst(nestedBlockSelector) != null) {
                    return@forEach
                }
                val hasBlockAncestor = element.parents().any { parent ->
                    parent != body && ancestorBlockTags.contains(parent.tagName().lowercase())
                }
                if (hasBlockAncestor) return@forEach
            }
            if (tag == "img") {
                val src = element.attr("src").trim()
                if (src.isBlank()) return@forEach
                val alt = element.attr("alt")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                val anchorHtml = buildAnchorMarkerHtml(element = element, body = body)
                paragraphs += CachedParagraph(
                    text = alt,
                    isHeading = false,
                    html = anchorHtml,
                    imageSrc = src
                )
                return@forEach
            }

            val text = element.text()
                .replace(Regex("\\s+"), " ")
                .trim()
            val htmlBlock = element
                .clone()
                .apply { select("img").remove() }
                .outerHtml()
                .trim()
            val inheritedAnchors = buildAncestorAnchorMarkerHtml(element = element, body = body)
            val htmlWithAnchors = (inheritedAnchors + htmlBlock).trim()
            if (text.isBlank() && htmlBlock.isBlank()) return@forEach
            if (text.isBlank() && isNonVisualHtmlBlock(htmlWithAnchors)) return@forEach
            val isHeading = tag.startsWith("h")
            val idx = paragraphs.size
            paragraphs += CachedParagraph(
                text = text,
                isHeading = isHeading,
                html = htmlWithAnchors
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
                    html = body.html()
                )
            }
        }

        return CachedBookContent(paragraphs = paragraphs, chapters = chapters)
    }

    private fun serialize(content: CachedBookContent): String {
        val root = JSONObject()
            .put("schemaVersion", CACHE_SCHEMA_VERSION)
        val paragraphsJson = JSONArray()
        content.paragraphs.forEach { paragraph ->
            paragraphsJson.put(
                JSONObject()
                    .put("text", paragraph.text)
                    .put("isHeading", paragraph.isHeading)
                    .put("html", paragraph.html)
                    .put("imageSrc", paragraph.imageSrc)
            )
        }
        val chaptersJson = JSONArray()
        content.chapters.forEach { chapter ->
            chaptersJson.put(
                JSONObject()
                    .put("title", chapter.title)
                    .put("paragraphIndex", chapter.paragraphIndex)
            )
        }
        root.put("paragraphs", paragraphsJson)
        root.put("chapters", chaptersJson)
        return root.toString()
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
                val imageSrc = obj.optString("imageSrc").trim().ifBlank { null }
                if (text.isBlank() && html.isBlank() && imageSrc.isNullOrBlank()) continue
                paragraphs += CachedParagraph(
                    text = text,
                    isHeading = obj.optBoolean("isHeading", false),
                    html = html,
                    imageSrc = imageSrc
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

    private fun escapeAttribute(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
}
