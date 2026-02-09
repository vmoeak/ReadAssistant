package com.readassistant.core.llm.provider

import com.google.gson.Gson
import com.readassistant.core.llm.api.*
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient
import javax.inject.Inject

class DeepSeekProvider @Inject constructor(
    private val httpClient: OkHttpClient,
    private val gson: Gson
) : LlmProvider {
    override val providerType = ProviderType.DEEPSEEK
    private val delegate by lazy { OpenAiProvider(httpClient, gson) }
    private fun adapt(config: LlmConfig) = config.copy(
        baseUrl = config.baseUrl.ifBlank { "https://api.deepseek.com" },
        modelName = config.modelName.ifBlank { "deepseek-chat" }
    )
    override suspend fun sendMessage(messages: List<LlmMessage>, config: LlmConfig) = delegate.sendMessage(messages, adapt(config))
    override fun streamMessage(messages: List<LlmMessage>, config: LlmConfig): Flow<LlmStreamChunk> = delegate.streamMessage(messages, adapt(config))
    override suspend fun testConnection(config: LlmConfig) = delegate.testConnection(adapt(config))
}
