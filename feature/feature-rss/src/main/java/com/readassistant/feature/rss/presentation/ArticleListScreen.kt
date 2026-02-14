package com.readassistant.feature.rss.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.readassistant.feature.rss.data.RssRepositoryImpl
import com.readassistant.feature.rss.domain.FeedArticle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class ArticleListViewModel @Inject constructor(savedStateHandle: SavedStateHandle, private val repository: RssRepositoryImpl) : ViewModel() {
    private val feedId: Long = savedStateHandle.get<Long>("feedId") ?: 0L
    val articles: StateFlow<List<FeedArticle>> = repository.getArticlesByFeed(feedId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun toggleStar(id: Long) { viewModelScope.launch { repository.toggleArticleStar(id) } }
    fun markRead(id: Long) { viewModelScope.launch { repository.markArticleRead(id) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleListScreen(onArticleClick: (Long) -> Unit, onBack: () -> Unit, viewModel: ArticleListViewModel = hiltViewModel()) {
    val articles by viewModel.articles.collectAsState()
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    Scaffold(topBar = { TopAppBar(title = { Text("Articles") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(articles, key = { it.id }) { article ->
                ListItem(
                    headlineContent = { Text(article.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = if (!article.isRead) FontWeight.Bold else FontWeight.Normal) },
                    supportingContent = { Column { val desc = article.description.replace(Regex("<[^>]*>"), "").trim(); if (desc.isNotBlank()) Text(desc.take(120), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall); Text(dateFormat.format(Date(article.publishedAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                    trailingContent = { IconButton(onClick = { viewModel.toggleStar(article.id) }) { Icon(if (article.isStarred) Icons.Default.Star else Icons.Default.StarBorder, contentDescription = "Star", tint = if (article.isStarred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) } },
                    modifier = Modifier.clickable { viewModel.markRead(article.id); onArticleClick(article.id) }
                )
            }
        }
    }
}
