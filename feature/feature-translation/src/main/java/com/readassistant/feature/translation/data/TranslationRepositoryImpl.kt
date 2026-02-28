package com.readassistant.feature.translation.data

import com.readassistant.core.data.db.dao.TranslationCacheDao
import com.readassistant.core.data.db.entity.TranslationCacheEntity
import com.readassistant.core.llm.api.LlmStreamChunk
import com.readassistant.core.llm.service.LlmService
import com.readassistant.core.llm.service.TranslationService
import com.readassistant.feature.translation.domain.TranslationPair
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranslationRepositoryImpl @Inject constructor(private val translationService: TranslationService, private val cacheDao: TranslationCacheDao, private val llmService: LlmService) {
    suspend fun getTranslation(idx: Int, text: String, src: String = "en", tgt: String = "zh"): Flow<TranslationPair> = flow {
        val hash = MessageDigest.getInstance("SHA-256").digest(text.trim().lowercase().toByteArray()).joinToString("") { "%02x".format(it) }
        val cached = cacheDao.getCachedTranslation(hash, src, tgt)
        if (cached != null) { emit(TranslationPair(idx, text, cached.translatedText, true, true)); return@flow }
        val config = llmService.getDefaultConfig()
        if (config == null) {
            emit(TranslationPair(idx, text, "[No LLM provider configured]", true))
            return@flow
        }
        var acc = ""
        translationService.streamTranslation(text, src, tgt, config).collect { chunk -> when (chunk) {
            is LlmStreamChunk.Delta -> { acc += chunk.text; emit(TranslationPair(idx, text, acc, false)) }
            is LlmStreamChunk.Done -> { cacheDao.insert(TranslationCacheEntity(contentHash = hash, sourceLang = src, targetLang = tgt, originalText = text, translatedText = acc)); emit(TranslationPair(idx, text, acc, true)) }
            is LlmStreamChunk.Error -> emit(TranslationPair(idx, text, "[Error: ${chunk.message}]", true))
        }}
    }
}
