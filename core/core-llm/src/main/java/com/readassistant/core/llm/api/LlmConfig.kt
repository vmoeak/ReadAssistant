package com.readassistant.core.llm.api

data class LlmConfig(
    val providerType: ProviderType = ProviderType.OPENAI,
    val apiKey: String = "",
    val baseUrl: String = "",
    val modelName: String = "",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 2048
)

enum class ProviderType {
    OPENAI, CLAUDE, GEMINI, DEEPSEEK, CUSTOM
}
