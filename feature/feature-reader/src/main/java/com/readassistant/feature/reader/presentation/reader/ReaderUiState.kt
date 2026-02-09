package com.readassistant.feature.reader.presentation.reader

import com.readassistant.core.domain.model.ContentType
import com.readassistant.core.domain.model.TextSelection

data class ReaderUiState(
    val isLoading: Boolean = true, val title: String = "", val htmlContent: String = "",
    val contentType: ContentType = ContentType.RSS_ARTICLE, val contentId: Long = 0,
    val currentChapterIndex: Int = 0, val totalChapters: Int = 0, val progressPercent: Float = 0f,
    val textSelection: TextSelection? = null, val showSelectionToolbar: Boolean = false,
    val showSettingsPanel: Boolean = false, val showChatSheet: Boolean = false, val isBilingualMode: Boolean = false, val error: String? = null
)
