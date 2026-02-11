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
import com.readassistant.feature.library.data.parser.BookParserFactory
import com.readassistant.feature.library.domain.BookFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle, private val articleDao: ArticleDao, private val bookDao: BookDao,
    private val webArticleDao: WebArticleDao, private val readingProgressDao: ReadingProgressDao,
    private val highlightDao: HighlightDao, private val noteDao: NoteDao, private val feedDao: FeedDao,
    private val userPreferences: UserPreferences,
    private val bookParserFactory: BookParserFactory,
    @ApplicationContext private val appContext: Context
) : ViewModel() {
    private val contentTypeStr: String = savedStateHandle.get<String>("contentType") ?: ""
    private val contentIdLong: Long = (savedStateHandle.get<String>("contentId") ?: "0").toLongOrNull() ?: 0L
    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()
    val themeType = userPreferences.themeType; val fontSize = userPreferences.fontSize; val lineHeight = userPreferences.lineHeight

    init {
        loadContent()
        viewModelScope.launch {
            noteDao.getNotesByContent(contentTypeStr, contentIdLong).collect { n -> _uiState.update { it.copy(notes = n) } }
        }
    }

    private fun loadContent() { viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true) }
        try { when (contentTypeStr) {
            "RSS_ARTICLE" -> {
                val a = articleDao.getArticleById(contentIdLong)
                if (a != null) {
                    System.out.println("ReaderViewModel: NOTE: Loading RSS Article: ${a.title}, isRead=${a.isRead}")
                    android.util.Log.e("ReaderViewModel", "Loading RSS Article: ${a.title}, isRead=${a.isRead}")
                    val content = a.extractedContent?.ifEmpty { null } ?: a.content
                    _uiState.update { it.copy(isLoading = false, title = a.title, htmlContent = content, contentType = ContentType.RSS_ARTICLE, contentId = contentIdLong) }
                    if (!a.isRead) {
                        articleDao.updateReadStatus(contentIdLong, true)
                        val unreadCount = articleDao.getUnreadCount(a.feedId)
                        feedDao.updateUnreadCount(a.feedId, unreadCount)
                        System.out.println("ReaderViewModel: NOTE: Marked read. Feed=${a.feedId}, NewCount=$unreadCount")
                        android.util.Log.e("ReaderViewModel", "Marked as read. New unread count for feed ${a.feedId}: $unreadCount")
                    } else {
                        System.out.println("ReaderViewModel: NOTE: Already read")
                        android.util.Log.e("ReaderViewModel", "Article already marked as read")
                    }
                } else {
                    System.out.println("ReaderViewModel: NOTE: Article with id $contentIdLong not found")
                    android.util.Log.e("ReaderViewModel", "Article with id $contentIdLong not found")
                    _uiState.update { it.copy(isLoading = false, error = "Article not found") }
                }
            }
            "WEB_ARTICLE" -> {
                val a = webArticleDao.getArticleById(contentIdLong)
                if (a != null) {
                    _uiState.update { it.copy(isLoading = false, title = a.title, htmlContent = a.content, contentType = ContentType.WEB_ARTICLE, contentId = contentIdLong) }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Article not found") }
                }
            }
            "BOOK" -> {
                val b = bookDao.getBookById(contentIdLong)
                if (b != null) {
                    val format = try { BookFormat.valueOf(b.format) } catch (_: Exception) { null }
                    val extractedContent = if (format != null) loadBookContentWithCache(b, format) else "<p>Unsupported format: ${b.format}</p>"
                    val htmlContent = extractedContent.ifBlank { "<p>No readable content extracted from this file.</p>" }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            title = b.title,
                            htmlContent = htmlContent,
                            contentType = try { ContentType.valueOf(b.format) } catch (_: Exception) { ContentType.EPUB },
                            contentId = contentIdLong,
                            currentChapterIndex = 0,
                            totalChapters = 0,
                            chapters = emptyList()
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Book not found") }
                }
            }
            else -> _uiState.update { it.copy(isLoading = false, error = "Unknown content type") }
        } } catch (e: Exception) { _uiState.update { it.copy(isLoading = false, error = e.message) } }
    }}

    private suspend fun loadBookContentWithCache(book: com.readassistant.core.data.db.entity.BookEntity, format: BookFormat): String {
        return withContext(Dispatchers.IO) {
            val source = File(book.filePath)
            val sourceMtime = runCatching { source.lastModified() }.getOrDefault(0L)
            val cacheDir = File(appContext.cacheDir, "book_content_cache").apply { mkdirs() }
            val cacheName = "book_${book.id}_${book.fileSize}_$sourceMtime.html"
            val cacheFile = File(cacheDir, cacheName)

            if (cacheFile.exists()) {
                return@withContext runCatching { cacheFile.readText(Charsets.UTF_8) }.getOrElse { "" }
            }

            val parsed = bookParserFactory.getParser(format).extractContent(book.filePath)
            if (parsed.isNotBlank()) {
                runCatching { cacheFile.writeText(parsed, Charsets.UTF_8) }
            }
            parsed
        }
    }

    fun saveProgress(p: Float) { viewModelScope.launch { val s = _uiState.value; readingProgressDao.upsert(ReadingProgressEntity(contentType = s.contentType.name, contentId = s.contentId, progressPercent = p)); _uiState.update { it.copy(progressPercent = p) } } }
    fun prevBookPage() {}
    fun nextBookPage() {}
    fun onBookPageChanged(currentPage: Int, totalPages: Int, progress: Float) {
        _uiState.update {
            it.copy(
                currentChapterIndex = currentPage.coerceAtLeast(0),
                totalChapters = totalPages.coerceAtLeast(1),
                progressPercent = progress.coerceIn(0f, 1f)
            )
        }
        viewModelScope.launch {
            val s = _uiState.value
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

    private fun isBookContentType(type: ContentType): Boolean = when (type) {
        ContentType.RSS_ARTICLE, ContentType.WEB_ARTICLE -> false
        else -> true
    }
}
