package com.readassistant.feature.library.data.cache

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.File
import java.util.LinkedHashMap

object BookParagraphCache {
    private const val CACHE_SCHEMA_VERSION = 1
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
        val isHeading: Boolean
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
        doc.select("script,style,iframe").remove()

        val paragraphs = mutableListOf<CachedParagraph>()
        val chapters = mutableListOf<CachedChapter>()
        val blockSelector = "h1,h2,h3,h4,h5,h6,p,li,blockquote,pre,div"
        val nestedBlockSelector = "p,h1,h2,h3,h4,h5,h6,li,blockquote,pre,table,ul,ol,div"

        doc.select(blockSelector).forEach { element ->
            if (element.tagName().equals("div", ignoreCase = true) &&
                element.selectFirst(nestedBlockSelector) != null
            ) {
                return@forEach
            }
            val text = element.text()
                .replace(Regex("\\s+"), " ")
                .trim()
            if (text.isBlank()) return@forEach
            val isHeading = element.tagName().startsWith("h", ignoreCase = true)
            val idx = paragraphs.size
            paragraphs += CachedParagraph(text = text, isHeading = isHeading)
            if (isHeading) {
                chapters += CachedChapter(
                    title = text.take(120),
                    paragraphIndex = idx
                )
            }
        }

        if (paragraphs.isEmpty()) {
            val fallback = doc.body().text()
                .replace(Regex("\\s+"), " ")
                .trim()
            if (fallback.isNotBlank()) {
                paragraphs += CachedParagraph(text = fallback, isHeading = false)
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
                if (text.isBlank()) continue
                paragraphs += CachedParagraph(
                    text = text,
                    isHeading = obj.optBoolean("isHeading", false)
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
}
