package com.readassistant.core.llm.service

import com.readassistant.core.llm.api.LlmConfig
import com.readassistant.core.llm.api.LlmMessage
import com.readassistant.core.llm.api.LlmStreamChunk
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QaService @Inject constructor(
    private val llmService: LlmService
) {
    private val systemPrompt = """You are a helpful reading assistant. The user is reading a text and has selected a passage to ask about.
Be concise and helpful. Support both English and Chinese responses based on the user's language."""

    fun streamAnswer(quotedText: String, conversationHistory: List<LlmMessage>, config: LlmConfig): Flow<LlmStreamChunk> {
        val messages = mutableListOf(LlmMessage("system", systemPrompt))
        if (quotedText.isNotBlank()) {
            messages.add(LlmMessage("user", "Selected text:\n\"\"\"$quotedText\"\"\""))
            messages.add(LlmMessage("assistant", "I've noted the selected text. How can I help you with it?"))
        }
        messages.addAll(conversationHistory.takeLast(20))
        return llmService.streamMessage(messages, config)
    }
}
