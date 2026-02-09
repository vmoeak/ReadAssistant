package com.readassistant.feature.reader.presentation.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.readassistant.core.data.db.dao.*
import com.readassistant.core.data.db.entity.HighlightEntity
import com.readassistant.core.data.db.entity.ReadingProgressEntity
import com.readassistant.core.data.datastore.UserPreferences
import com.readassistant.core.domain.model.ContentType
import com.readassistant.core.domain.model.TextSelection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle, private val articleDao: ArticleDao, private val bookDao: BookDao,
    private val webArticleDao: WebArticleDao, private val readingProgressDao: ReadingProgressDao,
    private val highlightDao: HighlightDao, private val userPreferences: UserPreferences
) : ViewModel() {
    private val contentTypeStr: String = savedStateHandle.get<String>("contentType") ?: ""
    private val contentIdLong: Long = (savedStateHandle.get<String>("contentId") ?: "0").toLongOrNull() ?: 0L
    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()
    val themeType = userPreferences.themeType; val fontSize = userPreferences.fontSize; val lineHeight = userPreferences.lineHeight

    init { loadContent() }

    private fun loadContent() { viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true) }
        try { when (contentTypeStr) {
            "RSS_ARTICLE" -> { val a = articleDao.getArticleById(contentIdLong); if (a != null) _uiState.update { it.copy(isLoading = false, title = a.title, htmlContent = a.extractedContent?.ifEmpty { null } ?: a.content, contentType = ContentType.RSS_ARTICLE, contentId = contentIdLong) } }
            "WEB_ARTICLE" -> { val a = webArticleDao.getArticleById(contentIdLong); if (a != null) _uiState.update { it.copy(isLoading = false, title = a.title, htmlContent = a.content, contentType = ContentType.WEB_ARTICLE, contentId = contentIdLong) } }
            "BOOK" -> { val b = bookDao.getBookById(contentIdLong); if (b != null) _uiState.update { it.copy(isLoading = false, title = b.title, htmlContent = "<p>Book: ${b.format}</p>", contentType = try { ContentType.valueOf(b.format) } catch (_: Exception) { ContentType.EPUB }, contentId = contentIdLong, totalChapters = b.totalChapters) } }
            else -> _uiState.update { it.copy(isLoading = false, error = "Unknown content type") }
        } } catch (e: Exception) { _uiState.update { it.copy(isLoading = false, error = e.message) } }
    }}

    fun saveProgress(p: Float) { viewModelScope.launch { val s = _uiState.value; readingProgressDao.upsert(ReadingProgressEntity(contentType = s.contentType.name, contentId = s.contentId, progressPercent = p)); _uiState.update { it.copy(progressPercent = p) } } }
    fun onTextSelected(sel: TextSelection) { _uiState.update { it.copy(textSelection = sel, showSelectionToolbar = true) } }
    fun clearSelection() { _uiState.update { it.copy(textSelection = null, showSelectionToolbar = false) } }
    fun addHighlight(color: String = "YELLOW") { val sel = _uiState.value.textSelection ?: return; viewModelScope.launch { highlightDao.insert(HighlightEntity(contentType = _uiState.value.contentType.name, contentId = _uiState.value.contentId, selectedText = sel.selectedText, color = color, paragraphIndex = sel.paragraphIndex)); clearSelection() } }
    fun toggleSettingsPanel() { _uiState.update { it.copy(showSettingsPanel = !it.showSettingsPanel) } }
    fun toggleBilingualMode() { _uiState.update { it.copy(isBilingualMode = !it.isBilingualMode) } }
    fun showChatSheet() { _uiState.update { it.copy(showChatSheet = true) } }
    fun hideChatSheet() { _uiState.update { it.copy(showChatSheet = false) } }
    fun updateFontSize(s: Float) { viewModelScope.launch { userPreferences.setFontSize(s) } }
    fun updateLineHeight(h: Float) { viewModelScope.launch { userPreferences.setLineHeight(h) } }
    fun updateTheme(t: String) { viewModelScope.launch { userPreferences.setThemeType(t) } }
}
