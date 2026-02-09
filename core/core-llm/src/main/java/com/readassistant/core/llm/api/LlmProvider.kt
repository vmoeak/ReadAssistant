package com.readassistant.core.llm.api

import kotlinx.coroutines.flow.Flow

interface LlmProvider {
    val providerType: ProviderType

    suspend fun sendMessage(messages: List<LlmMessage>, config: LlmConfig): String

    fun streamMessage(messages: List<LlmMessage>, config: LlmConfig): Flow<LlmStreamChunk>

    suspend fun testConnection(config: LlmConfig): Boolean
}
