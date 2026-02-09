package com.readassistant.core.llm.api

sealed class LlmStreamChunk {
    data class Delta(val text: String) : LlmStreamChunk()
    data class Error(val message: String, val code: Int = -1) : LlmStreamChunk()
    data object Done : LlmStreamChunk()
}
