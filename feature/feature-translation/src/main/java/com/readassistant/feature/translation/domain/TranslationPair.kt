package com.readassistant.feature.translation.domain

data class TranslationPair(val paragraphIndex: Int, val originalText: String, val translatedText: String, val isComplete: Boolean = false, val isFromCache: Boolean = false)
enum class TranslationState { IDLE, TRANSLATING, COMPLETED, ERROR }
