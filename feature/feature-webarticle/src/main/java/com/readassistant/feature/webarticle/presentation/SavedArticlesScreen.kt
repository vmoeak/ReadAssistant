package com.readassistant.feature.webarticle.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.readassistant.core.data.db.dao.WebArticleDao
import com.readassistant.core.data.db.entity.WebArticleEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat; import java.util.*
import javax.inject.Inject

@HiltViewModel
class SavedArticlesViewModel @Inject constructor(private val dao: WebArticleDao) : ViewModel() {
    val articles: StateFlow<List<WebArticleEntity>> = dao.getAllArticles().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun delete(a: WebArticleEntity) { viewModelScope.launch { dao.delete(a) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedArticlesScreen(onArticleClick: (Long) -> Unit, onAddClick: () -> Unit, viewModel: SavedArticlesViewModel = hiltViewModel()) {
    val articles by viewModel.articles.collectAsState(); val fmt = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    Scaffold(topBar = { TopAppBar(title = { Text("Articles") }) }, floatingActionButton = { FloatingActionButton(onClick = onAddClick) { Icon(Icons.Default.Add, "Add") } }) { padding ->
        if (articles.isEmpty()) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Language, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(16.dp)); Text("No saved articles"); Text("Tap + to add by URL") } }
        else LazyColumn(Modifier.fillMaxSize().padding(padding)) { items(articles, key = { it.id }) { a -> ListItem(headlineContent = { Text(a.title, maxLines = 2, overflow = TextOverflow.Ellipsis) }, supportingContent = { Column { Text((a.siteName ?: "").ifBlank { a.url }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall); Text(fmt.format(Date(a.savedAt)), style = MaterialTheme.typography.labelSmall) } }, leadingContent = { Icon(Icons.Default.Language, null) }, trailingContent = { IconButton(onClick = { viewModel.delete(a) }) { Icon(Icons.Default.Delete, "Delete") } }, modifier = Modifier.clickable { onArticleClick(a.id) }) } }
    }
}
