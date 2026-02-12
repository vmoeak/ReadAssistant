package com.readassistant.feature.library.data.reading

import android.content.Context
import com.readassistant.core.data.db.dao.BookDao
import com.readassistant.feature.library.data.cache.BookChapterCache
import com.readassistant.feature.library.data.cache.BookContentCache
import com.readassistant.feature.library.data.cache.BookParagraphCache
import com.readassistant.feature.library.data.parser.BookParserFactory
import com.readassistant.feature.library.domain.BookFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class LoadedBookContent(
    val id: Long,
    val title: String,
    val formatName: String,
    val structured: BookParagraphCache.CachedBookContent
)

@Singleton
class BookReadingRepository @Inject constructor(
    private val bookDao: BookDao,
    private val parserFactory: BookParserFactory,
    @ApplicationContext private val appContext: Context
) {
    suspend fun prewarm(bookId: Long) {
        withContext(Dispatchers.IO) {
            runCatching {
                val book = bookDao.getBookById(bookId) ?: return@runCatching
                val format = runCatching { BookFormat.valueOf(book.format) }.getOrNull() ?: return@runCatching

                val chapterCached = BookChapterCache.readCached(
                    context = appContext,
                    bookId = book.id,
                    fileSize = book.fileSize,
                    sourcePath = book.filePath
                )
                if (chapterCached == null) {
                    val html = loadHtmlContent(
                        bookId = book.id,
                        fileSize = book.fileSize,
                        sourcePath = book.filePath
                    ) {
                        parserFactory.getParser(format).extractContent(book.filePath, outputDir = appContext.cacheDir.absolutePath)
                    } ?: return@runCatching

                    val chapters = runCatching { BookChapterCache.buildFromHtml(html) }.getOrNull()
                        ?: return@runCatching
                    if (chapters.chapters.isNotEmpty() && book.totalChapters != chapters.chapters.size) {
                        runCatching { bookDao.update(book.copy(totalChapters = chapters.chapters.size)) }
                    }
                    BookChapterCache.writeCached(
                        context = appContext,
                        bookId = book.id,
                        fileSize = book.fileSize,
                        sourcePath = book.filePath,
                        chapters = chapters
                    )
                }

                val paragraphCached = BookParagraphCache.readCachedContent(
                    context = appContext,
                    bookId = book.id,
                    fileSize = book.fileSize,
                    sourcePath = book.filePath
                )
                if (paragraphCached == null) {
                    val html = loadHtmlContent(
                        bookId = book.id,
                        fileSize = book.fileSize,
                        sourcePath = book.filePath
                    ) {
                        parserFactory.getParser(format).extractContent(book.filePath, outputDir = appContext.cacheDir.absolutePath)
                    } ?: return@runCatching
                    val parsed = runCatching { BookParagraphCache.buildFromHtml(html) }.getOrNull()
                        ?: return@runCatching
                    if (parsed.paragraphs.isNotEmpty()) {
                        BookParagraphCache.writeCachedContent(
                            context = appContext,
                            bookId = book.id,
                            fileSize = book.fileSize,
                            sourcePath = book.filePath,
                            content = parsed
                        )
                    }
                }
            }
        }
    }

    suspend fun loadBookForReading(bookId: Long): LoadedBookContent? {
        return withContext(Dispatchers.IO) {
            runCatching {
                val book = bookDao.getBookById(bookId) ?: return@runCatching null
                val format = runCatching { BookFormat.valueOf(book.format) }.getOrNull()

                val structured = if (format == null) {
                    BookParagraphCache.CachedBookContent(
                        paragraphs = listOf(
                            BookParagraphCache.CachedParagraph(
                                text = "Unsupported format: ${book.format}",
                                isHeading = false
                            )
                        ),
                        chapters = emptyList()
                    )
                } else {
                    loadParagraphContent(
                        bookId = book.id,
                        fileSize = book.fileSize,
                        sourcePath = book.filePath
                    ) {
                        val html = loadHtmlContent(
                            bookId = book.id,
                            fileSize = book.fileSize,
                            sourcePath = book.filePath
                        ) {
                            android.util.Log.d("BookReadingRepository", "Calling parser for book ${book.id}")
                            parserFactory.getParser(format).extractContent(book.filePath, outputDir = appContext.cacheDir.absolutePath)
                        } ?: return@loadParagraphContent null
                        runCatching { BookParagraphCache.buildFromHtml(html) }.getOrNull()
                    } ?: return@runCatching null
                }

                LoadedBookContent(
                    id = book.id,
                    title = book.title,
                    formatName = book.format,
                    structured = structured
                )
            }.getOrNull()
        }
    }

    private suspend fun loadParagraphContent(
        bookId: Long,
        fileSize: Long,
        sourcePath: String,
        parse: suspend () -> BookParagraphCache.CachedBookContent?
    ): BookParagraphCache.CachedBookContent? {
        BookParagraphCache.readMemoryCachedContent(
            bookId = bookId,
            fileSize = fileSize,
            sourcePath = sourcePath
        )?.let { return it }

        BookParagraphCache.readCachedContent(
            context = appContext,
            bookId = bookId,
            fileSize = fileSize,
            sourcePath = sourcePath
        )?.let { return it }

        val parsed = runCatching { parse() }.getOrNull() ?: return null
        if (parsed.paragraphs.isNotEmpty()) {
            BookParagraphCache.writeCachedContent(
                context = appContext,
                bookId = bookId,
                fileSize = fileSize,
                sourcePath = sourcePath,
                content = parsed
            )
        }
        return parsed
    }

    private suspend fun loadHtmlContent(
        bookId: Long,
        fileSize: Long,
        sourcePath: String,
        parse: suspend () -> String
    ): String? {
        BookContentCache.readCachedHtml(
            context = appContext,
            bookId = bookId,
            fileSize = fileSize,
            sourcePath = sourcePath
        )?.let { return it }

        val html = runCatching { BookContentCache.clampBookHtml(parse()) }.getOrNull() ?: return null
        if (html.isBlank()) return null
        BookContentCache.writeCachedHtml(
            context = appContext,
            bookId = bookId,
            fileSize = fileSize,
            sourcePath = sourcePath,
            html = html
        )
        return html
    }
}
