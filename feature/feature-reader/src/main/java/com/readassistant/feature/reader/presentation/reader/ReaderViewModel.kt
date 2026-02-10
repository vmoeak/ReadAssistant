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
import com.readassistant.feature.library.data.parser.BookParserFactory
import com.readassistant.feature.library.domain.BookFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import javax.inject.Inject

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle, private val articleDao: ArticleDao, private val bookDao: BookDao,
    private val webArticleDao: WebArticleDao, private val readingProgressDao: ReadingProgressDao,
    private val highlightDao: HighlightDao, private val noteDao: NoteDao, private val feedDao: FeedDao,
    private val userPreferences: UserPreferences,
    private val bookParserFactory: BookParserFactory
) : ViewModel() {
    private val contentTypeStr: String = savedStateHandle.get<String>("contentType") ?: ""
    private val contentIdLong: Long = (savedStateHandle.get<String>("contentId") ?: "0").toLongOrNull() ?: 0L
    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()
    val themeType = userPreferences.themeType; val fontSize = userPreferences.fontSize; val lineHeight = userPreferences.lineHeight
    private var bookPages: List<String> = emptyList()

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
                    val extractedContent = if (format != null) {
                        withContext(Dispatchers.IO) {
                            bookParserFactory.getParser(format).extractContent(b.filePath)
                        }
                    } else {
                        "<p>Unsupported format: ${b.format}</p>"
                    }
                    val htmlContent = extractedContent.ifBlank { "<p>No readable content extracted from this file.</p>" }
                    val pages = paginateBookHtml(htmlContent)
                    bookPages = pages
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            title = b.title,
                            htmlContent = pages.firstOrNull().orEmpty(),
                            contentType = try { ContentType.valueOf(b.format) } catch (_: Exception) { ContentType.EPUB },
                            contentId = contentIdLong,
                            currentChapterIndex = 0,
                            totalChapters = pages.size
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Book not found") }
                }
            }
            else -> _uiState.update { it.copy(isLoading = false, error = "Unknown content type") }
        } } catch (e: Exception) { _uiState.update { it.copy(isLoading = false, error = e.message) } }
    }}

    fun saveProgress(p: Float) { viewModelScope.launch { val s = _uiState.value; readingProgressDao.upsert(ReadingProgressEntity(contentType = s.contentType.name, contentId = s.contentId, progressPercent = p)); _uiState.update { it.copy(progressPercent = p) } } }
    fun prevBookPage() { setBookPage(_uiState.value.currentChapterIndex - 1) }
    fun nextBookPage() { setBookPage(_uiState.value.currentChapterIndex + 1) }
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

    private fun setBookPage(targetIndex: Int) {
        if (!isBookContentType(_uiState.value.contentType)) return
        if (bookPages.isEmpty()) return
        val index = targetIndex.coerceIn(0, bookPages.lastIndex)
        if (index == _uiState.value.currentChapterIndex) return
        val progress = if (bookPages.size <= 1) 1f else index.toFloat() / bookPages.lastIndex.toFloat()
        _uiState.update { it.copy(currentChapterIndex = index, htmlContent = bookPages[index], progressPercent = progress) }
        viewModelScope.launch {
            val s = _uiState.value
            readingProgressDao.upsert(
                ReadingProgressEntity(contentType = s.contentType.name, contentId = s.contentId, progressPercent = progress)
            )
        }
    }

    private fun isBookContentType(type: ContentType): Boolean = when (type) {
        ContentType.RSS_ARTICLE, ContentType.WEB_ARTICLE -> false
        else -> true
    }

    private fun paginateBookHtml(
        html: String,
        blocksPerPage: Int = 5,
        maxCharsPerPage: Int = 900
    ): List<String> {
        val normalized = html.ifBlank { "<p>No readable content extracted from this file.</p>" }
        val doc = Jsoup.parseBodyFragment(normalized)
        val blocks = doc.body().children().flatMap { element ->
            val blockHtml = element.outerHtml().trim()
            if (blockHtml.isBlank()) return@flatMap emptyList()
            val blockTextLen = element.text().length
            if (blockTextLen <= maxCharsPerPage) return@flatMap listOf(blockHtml)

            // Split oversized blocks into text chunks to keep a single page close to one screen.
            val text = element.text().trim()
            if (text.isBlank()) return@flatMap listOf(blockHtml)
            text.chunked(maxCharsPerPage).map { chunk -> "<p>${chunk.replace("\n", "<br>")}</p>" }
        }.filter { it.isNotBlank() }
        if (blocks.isNotEmpty()) {
            val pages = mutableListOf<String>()
            val current = mutableListOf<String>()
            var currentChars = 0
            blocks.forEach { block ->
                val blockChars = Jsoup.parseBodyFragment(block).text().length.coerceAtLeast(1)
                val reachBlockLimit = current.size >= blocksPerPage
                val reachCharLimit = currentChars + blockChars > maxCharsPerPage
                if (current.isNotEmpty() && (reachBlockLimit || reachCharLimit)) {
                    pages.add(current.joinToString("\n"))
                    current.clear()
                    currentChars = 0
                }
                current.add(block)
                currentChars += blockChars
            }
            if (current.isNotEmpty()) pages.add(current.joinToString("\n"))
            if (pages.size > 1) return pages
        }

        val textBlocks = doc.body().text().split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotBlank() }
        if (textBlocks.isNotEmpty()) {
            val merged = textBlocks.joinToString("\n\n")
            if (merged.length > maxCharsPerPage) {
                return merged.chunked(maxCharsPerPage).map { chunk -> "<p>${chunk.replace("\n", "<br>")}</p>" }
            }
            return textBlocks.chunked(blocksPerPage).map { chunk -> chunk.joinToString("\n") { "<p>$it</p>" } }
        }
        return listOf("<p>No readable content extracted from this file.</p>")
    }
}
