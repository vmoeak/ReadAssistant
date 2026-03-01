package com.readassistant.feature.library.data.reading

import com.readassistant.feature.library.data.cache.BookParagraphCache
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton holding the active (most recently opened) book's fully loaded state.
 * Survives ViewModel recreation and navigation, enabling instant re-open of the same book
 * without any disk I/O or parsing.
 *
 * Also serves as a coordination point between the click-time prewarm and the
 * ReaderViewModel init, eliminating the race condition on BookParagraphCache's memory LRU.
 */
@Singleton
class ActiveBookState @Inject constructor() {
    @Volatile var bookId: Long = -1
        private set
    @Volatile var title: String = ""
        private set
    @Volatile var formatName: String = ""
        private set
    @Volatile var content: BookParagraphCache.CachedBookContent? = null
        private set

    fun isLoaded(id: Long): Boolean = bookId == id && content != null

    fun set(
        id: Long,
        title: String,
        formatName: String,
        content: BookParagraphCache.CachedBookContent
    ) {
        this.bookId = id
        this.title = title
        this.formatName = formatName
        this.content = content
    }

    fun toLoadedBookContent(): LoadedBookContent? {
        val c = content ?: return null
        if (bookId <= 0) return null
        return LoadedBookContent(
            id = bookId,
            title = title,
            formatName = formatName,
            structured = c
        )
    }

    fun clear() {
        bookId = -1
        title = ""
        formatName = ""
        content = null
    }
}
