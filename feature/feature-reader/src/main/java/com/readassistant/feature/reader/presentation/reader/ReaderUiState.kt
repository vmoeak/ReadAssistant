package com.readassistant.feature.reader.presentation.reader

import com.readassistant.core.domain.model.ContentType
import com.readassistant.core.domain.model.TextSelection

data class ReaderUiState(
    val isLoading: Boolean = true, val title: String = "", val htmlContent: String = "",
    val bookParagraphs: List<ReaderParagraph> = emptyList(),
    val contentType: ContentType = ContentType.RSS_ARTICLE, val contentId: Long = 0,
    val currentChapterIndex: Int = 0, val totalChapters: Int = 0, val progressPercent: Float = 0f,
    val textSelection: TextSelection? = null, val showSelectionToolbar: Boolean = false,
    val showSettingsPanel: Boolean = false, val showChatSheet: Boolean = false, val isBilingualMode: Boolean = false, val error: String? = null,
    val notes: List<com.readassistant.core.data.db.entity.NoteWithHighlight> = emptyList(),
    val chapters: List<ReaderChapter> = emptyList(),
    val savedBookPageIndex: Int? = null
)
