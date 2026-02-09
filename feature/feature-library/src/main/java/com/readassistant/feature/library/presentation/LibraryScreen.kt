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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(bookDao: BookDao) : ViewModel() {
    val books: StateFlow<List<BookEntity>> = bookDao.getAllBooks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
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
                    Card(Modifier.fillMaxWidth().aspectRatio(0.65f).clickable { onBookClick(book.id) }) {
                        Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Book, null, Modifier.size(32.dp)); Spacer(Modifier.height(8.dp)); Text(book.title, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
            }
        }
    }
}
