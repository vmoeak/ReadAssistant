package com.readassistant.core.llm.service

import com.google.gson.Gson
import com.readassistant.core.data.db.dao.LlmProviderDao
import com.readassistant.core.llm.api.*
import com.readassistant.core.llm.provider.*
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlmService @Inject constructor(
    private val llmProviderDao: LlmProviderDao,
    private val httpClient: OkHttpClient,
    private val gson: Gson
) {
    private fun getProvider(type: ProviderType): LlmProvider = when (type) {
        ProviderType.OPENAI -> OpenAiProvider(httpClient, gson)
        ProviderType.CLAUDE -> ClaudeProvider(httpClient, gson)
        ProviderType.GEMINI -> GeminiProvider(httpClient, gson)
        ProviderType.DEEPSEEK -> DeepSeekProvider(httpClient, gson)
        ProviderType.CUSTOM -> CustomOpenAiProvider(httpClient, gson)
    }

    suspend fun getDefaultConfig(): LlmConfig? {
        val entity = llmProviderDao.getDefaultProvider() ?: return null
        return LlmConfig(
            providerType = ProviderType.valueOf(entity.providerType),
            apiKey = entity.apiKey,
            baseUrl = entity.baseUrl,
            modelName = entity.modelName
        )
    }

    suspend fun sendMessage(messages: List<LlmMessage>, config: LlmConfig? = null): String {
        val c = config ?: getDefaultConfig() ?: throw IllegalStateException("No LLM provider configured")
        return getProvider(c.providerType).sendMessage(messages, c)
    }

    fun streamMessage(messages: List<LlmMessage>, config: LlmConfig): Flow<LlmStreamChunk> =
        getProvider(config.providerType).streamMessage(messages, config)

    suspend fun testConnection(config: LlmConfig): Boolean =
        getProvider(config.providerType).testConnection(config)
}
