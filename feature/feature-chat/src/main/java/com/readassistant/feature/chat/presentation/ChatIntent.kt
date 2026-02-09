package com.readassistant.feature.chat.presentation

sealed class ChatIntent {
    data class InitWithSelection(val selectedText: String) : ChatIntent()
    data class SendMessage(val text: String) : ChatIntent()
    data object CancelStreaming : ChatIntent()
    data object ClearHistory : ChatIntent()
    data class UpdateInput(val text: String) : ChatIntent()
}
