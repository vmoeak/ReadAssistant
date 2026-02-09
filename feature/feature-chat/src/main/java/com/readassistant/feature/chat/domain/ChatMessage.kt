package com.readassistant.feature.chat.domain

import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false
)

data class ChatSession(
    val messages: List<ChatMessage> = emptyList(),
    val quotedText: String = ""
)
