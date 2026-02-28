package com.readassistant.feature.reader.presentation.reader

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.readassistant.core.data.db.dao.*
import com.readassistant.core.data.db.entity.HighlightEntity
import com.readassistant.core.data.db.entity.ReadingProgressEntity
import com.readassistant.core.data.datastore.UserPreferences
import com.readassistant.core.domain.model.ContentType
import com.readassistant.core.domain.model.TextSelection
import com.readassistant.feature.library.data.cache.BookParagraphCache
import com.readassistant.feature.library.data.reading.BookReadingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle, private val articleDao: ArticleDao,
    private val webArticleDao: WebArticleDao, private val readingProgressDao: ReadingProgressDao,
    private val highlightDao: HighlightDao, private val noteDao: NoteDao, private val feedDao: FeedDao,
    private val userPreferences: UserPreferences,
    private val bookReadingRepository: BookReadingRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {
    private val bookPagePrefs = appContext.getSharedPreferences("reader_book_page_state", Context.MODE_PRIVATE)
    private val contentTypeStr: String = savedStateHandle.get<String>("contentType") ?: ""
    private val contentIdLong: Long = (savedStateHandle.get<String>("contentId") ?: "0").toLongOrNull() ?: 0L
    private val _uiState = MutableStateFlow(
        ReaderUiState(
            isLoading = contentTypeStr != "BOOK",
            contentType = when (contentTypeStr) {
                "WEB_ARTICLE" -> ContentType.WEB_ARTICLE
                "BOOK" -> ContentType.EPUB
                else -> ContentType.RSS_ARTICLE
            },
            contentId = contentIdLong
        )
    )
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()
    val themeType = userPreferences.themeType; val fontSize = userPreferences.fontSize; val lineHeight = userPreferences.lineHeight
    private var pendingInitialBookProgress: Float? = null
    private var pendingInitialBookPageIndex: Int? = null

    init {
        // Fast path for books: try memory cache first for instant open ("秒开")
        if (contentTypeStr == "BOOK" && contentIdLong > 0) {
            viewModelScope.launch {
                val cached = bookReadingRepository.tryLoadFromMemoryCache(contentIdLong)
                if (cached != null && _uiState.value.bookParagraphs.isEmpty()) {
                    val contentType = try { ContentType.valueOf(cached.formatName) } catch (_: Exception) { ContentType.EPUB }
                    val savedProgress = readSavedProgress(contentType.name, contentIdLong)
                    val savedPageIndex = readSavedBookPageIndex(contentIdLong)
                    applyBookContent(
                        title = cached.title,
                        formatName = cached.formatName,
                        contentId = contentIdLong,
                        structured = cached.structured,
                        initialProgress = savedProgress,
                        initialPageIndex = savedPageIndex
                    )
                    android.util.Log.w("ReadAssistant", "Book $contentIdLong opened instantly from memory cache")
                } else {
                    // Memory cache miss — fall back to normal loading
                    loadContent()
                }
            }
        } else {
            loadContent()
        }
        viewModelScope.launch {
            noteDao.getNotesByContent(_uiState.value.contentType.name, contentIdLong).collect { n -> _uiState.update { it.copy(notes = n) } }
        }
    }

    private fun loadContent() { viewModelScope.launch {
        android.util.Log.w("ReadAssistant", "loadContent START type=$contentTypeStr id=$contentIdLong")
        _uiState.update { it.copy(isLoading = contentTypeStr != "BOOK") }
        try { when (contentTypeStr) {
            "RSS_ARTICLE" -> {
                val a = articleDao.getArticleById(contentIdLong)
                if (a != null) {
                    val content = a.extractedContent?.ifEmpty { null }
                        ?: a.content.ifEmpty { null }
                        ?: a.description
                    val savedProgress = readSavedProgress(ContentType.RSS_ARTICLE.name, contentIdLong)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            title = a.title,
                            htmlContent = content,
                            contentType = ContentType.RSS_ARTICLE,
                            contentId = contentIdLong,
                            progressPercent = savedProgress,
                            originalLink = a.link
                        )
                    }
                    android.util.Log.w("ReadAssistant", "RSS loaded: title=${a.title} htmlLen=${content.length}")
                    if (!a.isRead) {
                        articleDao.updateReadStatus(contentIdLong, true)
                        val unreadCount = articleDao.getUnreadCount(a.feedId)
                        feedDao.updateUnreadCount(a.feedId, unreadCount)
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Article not found") }
                }
            }
            "WEB_ARTICLE" -> {
                val a = webArticleDao.getArticleById(contentIdLong)
                android.util.Log.w("ReadAssistant", "WEB_ARTICLE fetched: found=${a != null} id=$contentIdLong title=${a?.title} contentLen=${a?.content?.length}")
                if (a != null) {
                    val savedProgress = readSavedProgress(ContentType.WEB_ARTICLE.name, contentIdLong)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            title = a.title,
                            htmlContent = a.content,
                            contentType = ContentType.WEB_ARTICLE,
                            contentId = contentIdLong,
                            progressPercent = savedProgress,
                            originalLink = a.url
                        )
                    }
                    android.util.Log.w("ReadAssistant", "WEB_ARTICLE uiState: isLoading=${_uiState.value.isLoading} htmlLen=${_uiState.value.htmlContent.length}")
                } else {
                    android.util.Log.w("ReadAssistant", "WEB_ARTICLE not found id=$contentIdLong")
                    _uiState.update { it.copy(isLoading = false, error = "Article not found") }
                }
            }
            "BOOK" -> {
                val loaded = bookReadingRepository.loadBookForReading(contentIdLong)
                if (loaded != null) {
                    val contentType = try { ContentType.valueOf(loaded.formatName) } catch (_: Exception) { ContentType.EPUB }
                    val savedProgress = readSavedProgress(contentType.name, contentIdLong)
                    val savedPageIndex = readSavedBookPageIndex(contentIdLong)
                    applyBookContent(
                        title = loaded.title,
                        formatName = loaded.formatName,
                        contentId = contentIdLong,
                        structured = loaded.structured,
                        initialProgress = savedProgress,
                        initialPageIndex = savedPageIndex
                    )
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Book not found") }
                }
            }
            else -> _uiState.update { it.copy(isLoading = false, error = "Unknown content type") }
        } } catch (e: Throwable) { _uiState.update { it.copy(isLoading = false, error = e.message) } }
    }}

    private fun applyBookContent(
        title: String,
        formatName: String,
        contentId: Long,
        structured: BookParagraphCache.CachedBookContent,
        initialProgress: Float,
        initialPageIndex: Int?
    ) {
        val readerParagraphs = structured.paragraphs.mapIndexed { index, paragraph ->
            ReaderParagraph(
                index = index,
                text = paragraph.text,
                isHeading = paragraph.isHeading,
                html = paragraph.html,
                imageSrc = paragraph.imageSrc
            )
        }
        val readerChapters = structured.chapters.map {
            ReaderChapter(title = it.title, pageIndex = it.paragraphIndex)
        }
        pendingInitialBookProgress = initialProgress
        pendingInitialBookPageIndex = initialPageIndex?.coerceAtLeast(0)
        _uiState.update {
            it.copy(
                isLoading = false,
                title = title,
                htmlContent = "",
                bookParagraphs = readerParagraphs,
                contentType = try { ContentType.valueOf(formatName) } catch (_: Exception) { ContentType.EPUB },
                contentId = contentId,
                currentChapterIndex = 0,
                totalChapters = readerParagraphs.size.coerceAtLeast(1),
                chapters = readerChapters,
                progressPercent = initialProgress.coerceIn(0f, 1f),
                savedBookPageIndex = initialPageIndex?.coerceAtLeast(0)
            )
        }
    }

    fun saveProgress(p: Float) { viewModelScope.launch { val s = _uiState.value; readingProgressDao.upsert(ReadingProgressEntity(contentType = s.contentType.name, contentId = s.contentId, progressPercent = p)); _uiState.update { it.copy(progressPercent = p) } } }
    fun prevBookPage() {}
    fun nextBookPage() {}
    fun onBookPageChanged(currentPage: Int, totalPages: Int, progress: Float) {
        val normalizedProgress = progress.coerceIn(0f, 1f)
        val currentState = _uiState.value
        if (currentState.bookParagraphs.isEmpty()) {
            return
        }
        val pendingProgress = pendingInitialBookProgress
        val pendingPageIndex = pendingInitialBookPageIndex
        if (pendingPageIndex != null && pendingPageIndex > 0 && currentPage <= 0) {
            return
        }
        if (pendingProgress != null && pendingProgress > 0.001f && normalizedProgress <= 0.0001f) {
            return
        }
        pendingInitialBookProgress = null
        pendingInitialBookPageIndex = null
        _uiState.update {
            it.copy(
                currentChapterIndex = currentPage.coerceAtLeast(0),
                totalChapters = totalPages.coerceAtLeast(1),
                progressPercent = normalizedProgress
            )
        }
        viewModelScope.launch {
            val s = _uiState.value
            saveBookPageIndex(s.contentId, currentPage)
            readingProgressDao.upsert(
                ReadingProgressEntity(
                    contentType = s.contentType.name,
                    contentId = s.contentId,
                    progressPercent = s.progressPercent
                )
            )
        }
    }
    fun onChaptersExtracted(chapters: List<ReaderChapter>) {
        if (chapters.isEmpty()) return
        val sorted = chapters
            .filter { it.title.isNotBlank() }
            .distinctBy { "${it.pageIndex}:${it.title}" }
            .sortedBy { it.pageIndex }
        _uiState.update { it.copy(chapters = sorted) }
    }
    fun onTextSelected(sel: TextSelection) { _uiState.update { it.copy(textSelection = sel, showSelectionToolbar = true) } }
    fun clearSelection() { _uiState.update { it.copy(textSelection = null, showSelectionToolbar = false) } }
    fun addHighlight(color: String = "YELLOW") { val sel = _uiState.value.textSelection ?: return; viewModelScope.launch { highlightDao.insert(HighlightEntity(contentType = _uiState.value.contentType.name, contentId = _uiState.value.contentId, selectedText = sel.selectedText, color = color, paragraphIndex = sel.paragraphIndex)); clearSelection() } }
    fun addNote(text: String, color: String = "YELLOW") {
        val sel = _uiState.value.textSelection ?: return
        viewModelScope.launch {
            val hid = highlightDao.insert(HighlightEntity(contentType = _uiState.value.contentType.name, contentId = _uiState.value.contentId, selectedText = sel.selectedText, color = color, paragraphIndex = sel.paragraphIndex))
            noteDao.insert(com.readassistant.core.data.db.entity.NoteEntity(highlightId = hid, contentType = _uiState.value.contentType.name, contentId = _uiState.value.contentId, noteText = text))
            clearSelection()
        }
    }
    fun deleteNote(note: com.readassistant.core.data.db.entity.NoteWithHighlight) {
        viewModelScope.launch { noteDao.delete(note.note) }
    }
    fun toggleSettingsPanel() { _uiState.update { it.copy(showSettingsPanel = !it.showSettingsPanel) } }
    fun toggleBilingualMode() { _uiState.update { it.copy(isBilingualMode = !it.isBilingualMode) } }
    fun showChatSheet() { _uiState.update { it.copy(showChatSheet = true) } }
    fun hideChatSheet() { _uiState.update { it.copy(showChatSheet = false) } }
    fun updateFontSize(s: Float) { viewModelScope.launch { userPreferences.setFontSize(s) } }
    fun updateLineHeight(h: Float) { viewModelScope.launch { userPreferences.setLineHeight(h) } }
    fun updateTheme(t: String) { viewModelScope.launch { userPreferences.setThemeType(t) } }

    private suspend fun readSavedProgress(contentType: String, contentId: Long): Float {
        return withContext(Dispatchers.IO) {
            readingProgressDao.getProgress(contentType, contentId)?.progressPercent ?: 0f
        }.coerceIn(0f, 1f)
    }

    private fun readSavedBookPageIndex(contentId: Long): Int? {
        val page = bookPagePrefs.getInt("book_page_$contentId", -1)
        return page.takeIf { it >= 0 }
    }

    private fun saveBookPageIndex(contentId: Long, pageIndex: Int) {
        if (contentId <= 0) return
        bookPagePrefs.edit().putInt("book_page_$contentId", pageIndex.coerceAtLeast(0)).apply()
    }

}
