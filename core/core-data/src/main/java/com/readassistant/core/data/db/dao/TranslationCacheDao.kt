package com.readassistant.core.data.db.dao

import androidx.room.*
import com.readassistant.core.data.db.entity.TranslationCacheEntity

@Dao
interface TranslationCacheDao {
    @Query("SELECT * FROM translation_cache WHERE contentHash = :hash AND sourceLang = :source AND targetLang = :target")
    suspend fun getCachedTranslation(hash: String, source: String, target: String): TranslationCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cache: TranslationCacheEntity)

    @Query("DELETE FROM translation_cache WHERE createdAt < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("SELECT COUNT(*) FROM translation_cache")
    suspend fun getCacheSize(): Int
}
