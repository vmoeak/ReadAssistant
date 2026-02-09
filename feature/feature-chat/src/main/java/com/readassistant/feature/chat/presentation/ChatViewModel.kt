package com.readassistant.feature.chat.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.readassistant.core.llm.api.LlmConfig
import com.readassistant.core.llm.api.LlmMessage
import com.readassistant.core.llm.api.LlmStreamChunk
import com.readassistant.core.llm.service.LlmService
import com.readassistant.core.llm.service.QaService
import com.readassistant.feature.chat.domain.ChatMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val qaService: QaService,
    private val llmService: LlmService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val intentChannel = Channel<ChatIntent>(Channel.UNLIMITED)
    private var streamingJob: Job? = null

    init {
        viewModelScope.launch {
            for (intent in intentChannel) {
                processIntent(intent)
            }
        }
    }

    fun sendIntent(intent: ChatIntent) {
        intentChannel.trySend(intent)
    }

    private suspend fun processIntent(intent: ChatIntent) {
        when (intent) {
            is ChatIntent.InitWithSelection -> {
                _uiState.update { it.copy(quotedText = intent.selectedText, messages = emptyList(), error = null) }
            }
            is ChatIntent.SendMessage -> {
                val userMessage = ChatMessage(role = "user", content = intent.text)
                _uiState.update { it.copy(
                    messages = it.messages + userMessage,
                    inputText = "",
                    isStreaming = true,
                    error = null
                )}
                streamResponse()
            }
            is ChatIntent.CancelStreaming -> {
                streamingJob?.cancel()
                _uiState.update { state ->
                    val msgs = state.messages.map { if (it.isStreaming) it.copy(isStreaming = false) else it }
                    state.copy(messages = msgs, isStreaming = false)
                }
            }
            is ChatIntent.ClearHistory -> {
                streamingJob?.cancel()
                _uiState.update { ChatUiState(quotedText = it.quotedText) }
            }
            is ChatIntent.UpdateInput -> {
                _uiState.update { it.copy(inputText = intent.text) }
            }
        }
    }

    private fun streamResponse() {
        streamingJob = viewModelScope.launch {
            val state = _uiState.value
            val assistantMsg = ChatMessage(role = "assistant", content = "", isStreaming = true)
            _uiState.update { it.copy(messages = it.messages + assistantMsg) }

            try {
                val config = llmService.getDefaultConfig() ?: throw IllegalStateException("No LLM configured")
                val history = state.messages.map { LlmMessage(it.role, it.content) }
                qaService.streamAnswer(state.quotedText, history, config)
                    .collect { chunk ->
                        when (chunk) {
                            is LlmStreamChunk.Delta -> {
                                _uiState.update { s ->
                                    val msgs = s.messages.toMutableList()
                                    val last = msgs.last()
                                    msgs[msgs.lastIndex] = last.copy(content = last.content + chunk.text)
                                    s.copy(messages = msgs)
                                }
                            }
                            is LlmStreamChunk.Error -> {
                                _uiState.update { it.copy(error = chunk.message, isStreaming = false) }
                            }
                            is LlmStreamChunk.Done -> {
                                _uiState.update { s ->
                                    val msgs = s.messages.toMutableList()
                                    val last = msgs.last()
                                    msgs[msgs.lastIndex] = last.copy(isStreaming = false)
                                    s.copy(messages = msgs, isStreaming = false)
                                }
                            }
                        }
                    }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isStreaming = false) }
            }
        }
    }
}
