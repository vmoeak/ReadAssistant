package com.readassistant.core.llm.provider

import com.google.gson.Gson
import com.readassistant.core.llm.api.*
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient
import javax.inject.Inject

class CustomOpenAiProvider @Inject constructor(
    private val httpClient: OkHttpClient,
    private val gson: Gson
) : LlmProvider {
    override val providerType = ProviderType.CUSTOM
    private val delegate by lazy { OpenAiProvider(httpClient, gson) }
    override suspend fun sendMessage(messages: List<LlmMessage>, config: LlmConfig) = delegate.sendMessage(messages, config)
    override fun streamMessage(messages: List<LlmMessage>, config: LlmConfig): Flow<LlmStreamChunk> = delegate.streamMessage(messages, config)
    override suspend fun testConnection(config: LlmConfig) = delegate.testConnection(config)
}
