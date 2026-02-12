package com.readassistant.feature.library.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.readassistant.core.data.db.dao.BookDao
import com.readassistant.core.data.db.entity.BookEntity
import com.readassistant.feature.library.data.reading.BookReadingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(onBookClick: (Long) -> Unit, onImportClick: () -> Unit, viewModel: LibraryViewModel = hiltViewModel()) {
    val books by viewModel.books.collectAsState()
    Scaffold(topBar = { TopAppBar(title = { Text("Library") }) }, floatingActionButton = { FloatingActionButton(onClick = onImportClick) { Icon(Icons.Default.Add, contentDescription = "Import") } }) { padding ->
        if (books.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Book, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(16.dp)); Text("No books yet", style = MaterialTheme.typography.titleMedium); Text("Tap + to import a book") }
            }
        } else {
            LazyVerticalGrid(columns = GridCells.Adaptive(120.dp), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize().padding(padding)) {
                items(books, key = { it.id }) { book ->
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.65f)
                            .clickable { onBookClick(book.id) }
                    ) {
                        Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Book, null, Modifier.size(32.dp))
                                Spacer(Modifier.height(8.dp))
                                Text(book.title, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (book.author.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(book.author, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (book.totalChapters > 0) {
                                    Spacer(Modifier.height(2.dp))
                                    Text("${book.totalChapters} chapters", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
