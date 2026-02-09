package com.readassistant.feature.chat.presentation

import com.readassistant.feature.chat.domain.ChatMessage

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val quotedText: String = "",
    val inputText: String = "",
    val isStreaming: Boolean = false,
    val error: String? = null
)
