package com.readassistant.feature.library.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import dagger.hilt.android.qualifiers.ApplicationContext
import com.readassistant.core.data.db.dao.BookDao
import com.readassistant.core.data.db.entity.BookEntity
import com.readassistant.feature.library.data.reading.BookReadingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val bookDao: BookDao,
    private val bookReadingRepository: BookReadingRepository
) : ViewModel() {
    private val prewarmedIds = mutableSetOf<Long>()
    private val booksFlow = bookDao.getAllBooks()
    val books: StateFlow<List<BookEntity>> = booksFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            booksFlow.collectLatest { list ->
                // Prewarm only the first few recently added books to avoid heavy background work.
                list.take(3).forEach { book ->
                    if (prewarmedIds.add(book.id)) prewarmBookContentCache(book)
                }
            }
        }
    }

    private suspend fun prewarmBookContentCache(book: BookEntity) = withContext(Dispatchers.IO) {
        runCatching { bookReadingRepository.prewarm(book.id) }
    }

    suspend fun prepareBookForOpen(bookId: Long) = withContext(Dispatchers.IO) {
        val book = bookDao.getBookById(bookId) ?: return@withContext
        prewarmBookContentCache(book)
    }

    fun deleteBook(book: BookEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                bookDao.delete(book)
                cleanupBookFiles(book)
            }
        }
    }

    private fun cleanupBookFiles(book: BookEntity) {
        val file = File(book.filePath)
        val booksRoot = File(appContext.filesDir, "books")
        val parent = file.parentFile
        if (parent != null && parent.exists() && parent.toPath().startsWith(booksRoot.toPath())) {
            parent.deleteRecursively()
        } else {
            if (file.exists()) file.delete()
            book.coverPath?.takeIf { it.isNotBlank() }?.let { cover ->
                runCatching { File(cover).delete() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(onBookClick: (Long) -> Unit, onImportClick: () -> Unit, viewModel: LibraryViewModel = hiltViewModel()) {
    val books by viewModel.books.collectAsState()
    val scope = rememberCoroutineScope()
    var pendingDeleteBook by remember { mutableStateOf<BookEntity?>(null) }
    Scaffold(topBar = { TopAppBar(title = { Text("Library") }) }, floatingActionButton = { FloatingActionButton(onClick = onImportClick) { Icon(Icons.Default.Add, contentDescription = "Import") } }) { padding ->
        if (books.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Book, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(16.dp)); Text("No books yet", style = MaterialTheme.typography.titleMedium); Text("Tap + to import a book") }
            }
        } else {
            LazyVerticalGrid(columns = GridCells.Adaptive(138.dp), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize().padding(padding)) {
                items(books, key = { it.id }) { book ->
                    ElevatedCard(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.62f)
                            .clickable {
                                // Prewarm book content cache in parallel with navigation for instant open
                                scope.launch { viewModel.prepareBookForOpen(book.id) }
                                onBookClick(book.id)
                            }
                    ) {
                        Column(Modifier.fillMaxSize().padding(10.dp)) {
                            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                BookCover(book = book, modifier = Modifier.fillMaxSize())
                                IconButton(
                                    onClick = { pendingDeleteBook = book },
                                    modifier = Modifier.align(Alignment.TopEnd)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete book",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = book.title,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                minLines = 2,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = book.author.ifBlank { "Unknown author" },
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (book.totalChapters > 0) {
                                Spacer(Modifier.height(6.dp))
                                AssistChip(
                                    onClick = {},
                                    enabled = false,
                                    label = { Text("${book.totalChapters} chapters") },
                                    leadingIcon = { Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (pendingDeleteBook != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteBook = null },
            title = { Text("Delete book") },
            text = { Text("Delete \"${pendingDeleteBook?.title ?: ""}\" from library?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeleteBook?.let { viewModel.deleteBook(it) }
                        pendingDeleteBook = null
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteBook = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun BookCover(book: BookEntity, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.medium
    val model = remember(book.coverPath) { book.coverPath?.takeIf { it.isNotBlank() } }
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.surfaceVariant,
                                Color(0xFFE8EEF5)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Book,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = (book.format.ifBlank { "BOOK" }).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                    )
                }
            }
        }
    }
}
