package com.readassistant.core.llm.service

import com.readassistant.core.llm.api.LlmConfig
import com.readassistant.core.llm.api.LlmMessage
import com.readassistant.core.llm.api.LlmStreamChunk
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranslationService @Inject constructor(
    private val llmService: LlmService
) {
    private fun systemPrompt(src: String, tgt: String) =
        """You are a professional translator. Translate from $src to $tgt.
Rules: Output ONLY the translated text. Preserve formatting. Maintain tone and style. Translate naturally."""

    fun streamTranslation(text: String, sourceLang: String = "en", targetLang: String = "zh", config: LlmConfig): Flow<LlmStreamChunk> {
        val messages = listOf(LlmMessage("system", systemPrompt(sourceLang, targetLang)), LlmMessage("user", text))
        return llmService.streamMessage(messages, config.copy(temperature = 0.3f))
    }

    suspend fun translate(text: String, sourceLang: String = "en", targetLang: String = "zh", config: LlmConfig? = null): String {
        val messages = listOf(LlmMessage("system", systemPrompt(sourceLang, targetLang)), LlmMessage("user", text))
        return llmService.sendMessage(messages, config?.copy(temperature = 0.3f))
    }
}
