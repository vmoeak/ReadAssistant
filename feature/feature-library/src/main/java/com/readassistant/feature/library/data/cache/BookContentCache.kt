package com.readassistant.feature.library.data.cache

import android.content.Context
import java.io.File

object BookContentCache {
    const val CACHE_SCHEMA_VERSION = 11
    private const val CACHE_DIR_NAME = "book_content_cache"

    fun readCachedHtml(
        context: Context,
        bookId: Long,
        fileSize: Long,
        sourcePath: String
    ): String? {
        val cacheFile = buildCacheFile(context, bookId, fileSize, sourcePath)
        if (!cacheFile.exists()) return null
        return runCatching { clampBookHtml(cacheFile.readText(Charsets.UTF_8)) }.getOrNull()
    }

    fun writeCachedHtml(
        context: Context,
        bookId: Long,
        fileSize: Long,
        sourcePath: String,
        html: String
    ) {
        val normalized = clampBookHtml(html)
        if (normalized.isBlank()) return
        val cacheFile = buildCacheFile(context, bookId, fileSize, sourcePath)
        runCatching { cacheFile.writeText(normalized, Charsets.UTF_8) }
    }

    fun clampBookHtml(html: String): String {
        return html
    }

    private fun buildCacheFile(
        context: Context,
        bookId: Long,
        fileSize: Long,
        sourcePath: String
    ): File {
        val source = File(sourcePath)
        val sourceMtime = runCatching { source.lastModified() }.getOrDefault(0L)
        val cacheDir = File(context.cacheDir, CACHE_DIR_NAME).apply { mkdirs() }
        val cacheName = "v${CACHE_SCHEMA_VERSION}_book_${bookId}_${fileSize}_$sourceMtime.html"
        return File(cacheDir, cacheName)
    }
}
