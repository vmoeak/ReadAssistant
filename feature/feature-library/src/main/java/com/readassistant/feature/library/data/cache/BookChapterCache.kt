package com.readassistant.feature.library.data.cache

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.File

object BookChapterCache {
    private const val CACHE_SCHEMA_VERSION = 1
    private const val CACHE_DIR_NAME = "book_chapter_cache"

    data class CachedChapter(
        val title: String,
        val content: String
    )

    data class CachedBookChapters(
        val chapters: List<CachedChapter>
    )

    fun readCached(
        context: Context,
        bookId: Long,
        fileSize: Long,
        sourcePath: String
    ): CachedBookChapters? {
        val file = buildCacheFile(context, bookId, fileSize, sourcePath)
        if (!file.exists()) return null
        val raw = runCatching { file.readText(Charsets.UTF_8) }.getOrNull() ?: return null
        return parse(raw)
    }

    fun writeCached(
        context: Context,
        bookId: Long,
        fileSize: Long,
        sourcePath: String,
        chapters: CachedBookChapters
    ) {
        if (chapters.chapters.isEmpty()) return
        val file = buildCacheFile(context, bookId, fileSize, sourcePath)
        runCatching {
            val root = JSONObject()
            root.put("schemaVersion", CACHE_SCHEMA_VERSION)
            val chapterArray = JSONArray()
            chapters.chapters.forEach { chapter ->
                chapterArray.put(
                    JSONObject()
                        .put("title", chapter.title)
                        .put("content", chapter.content)
                )
            }
            root.put("chapters", chapterArray)
            file.writeText(root.toString(), Charsets.UTF_8)
        }.onFailure {
            Log.w("BookChapterCache", "write cache failed: ${file.name}", it)
        }
    }

    fun buildFromHtml(html: String): CachedBookChapters {
        val doc = Jsoup.parse(html)
        doc.select("script,style,iframe,noscript").remove()
        val body = doc.body()

        val chapters = mutableListOf<CachedChapter>()
        val paragraphBuffer = mutableListOf<String>()
        var currentTitle = "正文"

        val blocks = body.select("h1,h2,h3,h4,h5,h6,p,li,blockquote,pre,div")
        blocks.forEach { element ->
            val tag = element.tagName().lowercase()
            if (tag == "div" && element.selectFirst("h1,h2,h3,h4,h5,h6,p,li,blockquote,pre") != null) {
                return@forEach
            }
            val text = element.text()
                .replace(Regex("\\s+"), " ")
                .trim()
            if (text.isBlank()) return@forEach

            if (tag.startsWith("h")) {
                if (paragraphBuffer.isNotEmpty()) {
                    chapters += CachedChapter(
                        title = currentTitle.ifBlank { "正文" },
                        content = paragraphBuffer.joinToString("\n\n")
                    )
                    paragraphBuffer.clear()
                }
                currentTitle = text.take(120)
            } else {
                paragraphBuffer += text
            }
        }

        if (paragraphBuffer.isNotEmpty()) {
            chapters += CachedChapter(
                title = currentTitle.ifBlank { "正文" },
                content = paragraphBuffer.joinToString("\n\n")
            )
        }

        if (chapters.isNotEmpty()) return CachedBookChapters(chapters)

        val fallback = body.text().replace(Regex("\\s+"), " ").trim()
        if (fallback.isNotBlank()) {
            val regexSplit = splitByChapterRegex(fallback)
            if (regexSplit.isNotEmpty()) return CachedBookChapters(regexSplit)
            return CachedBookChapters(listOf(CachedChapter("正文", fallback)))
        }

        return CachedBookChapters(listOf(CachedChapter("正文", "")))
    }

    private fun splitByChapterRegex(text: String): List<CachedChapter> {
        val lines = text.split(Regex("(?<=。)|\\n")).map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        val titleRegex = Regex("^(第[0-9一二三四五六七八九十百千万零〇两]+[章回节卷部篇].*)$")
        val chapters = mutableListOf<CachedChapter>()
        var title = "正文"
        val content = mutableListOf<String>()

        lines.forEach { line ->
            if (titleRegex.matches(line) && content.isNotEmpty()) {
                chapters += CachedChapter(title = title, content = content.joinToString("\n\n"))
                title = line.take(120)
                content.clear()
            } else {
                content += line
            }
        }
        if (content.isNotEmpty()) {
            chapters += CachedChapter(title = title, content = content.joinToString("\n\n"))
        }
        return chapters
    }

    private fun parse(raw: String): CachedBookChapters? {
        return runCatching {
            val root = JSONObject(raw)
            val array = root.optJSONArray("chapters") ?: JSONArray()
            val chapters = mutableListOf<CachedChapter>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val title = obj.optString("title").trim().ifBlank { "正文" }
                val content = obj.optString("content").trim()
                chapters += CachedChapter(title = title, content = content)
            }
            if (chapters.isEmpty()) null else CachedBookChapters(chapters)
        }.getOrNull()
    }

    private fun buildCacheFile(
        context: Context,
        bookId: Long,
        fileSize: Long,
        sourcePath: String
    ): File {
        val cacheDir = File(context.cacheDir, CACHE_DIR_NAME).apply { mkdirs() }
        val source = File(sourcePath)
        val sourceMtime = runCatching { source.lastModified() }.getOrDefault(0L)
        return File(cacheDir, "v${CACHE_SCHEMA_VERSION}_book_${bookId}_${fileSize}_${sourceMtime}.json")
    }
}
