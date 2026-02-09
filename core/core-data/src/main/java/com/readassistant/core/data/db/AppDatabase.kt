package com.readassistant.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.readassistant.core.data.db.dao.*
import com.readassistant.core.data.db.entity.*

@Database(
    entities = [
        FeedEntity::class,
        ArticleEntity::class,
        BookEntity::class,
        ReadingProgressEntity::class,
        BookmarkEntity::class,
        HighlightEntity::class,
        NoteEntity::class,
        TranslationCacheEntity::class,
        LlmProviderEntity::class,
        WebArticleEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun feedDao(): FeedDao
    abstract fun articleDao(): ArticleDao
    abstract fun bookDao(): BookDao
    abstract fun readingProgressDao(): ReadingProgressDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun highlightDao(): HighlightDao
    abstract fun noteDao(): NoteDao
    abstract fun translationCacheDao(): TranslationCacheDao
    abstract fun llmProviderDao(): LlmProviderDao
    abstract fun webArticleDao(): WebArticleDao
}
