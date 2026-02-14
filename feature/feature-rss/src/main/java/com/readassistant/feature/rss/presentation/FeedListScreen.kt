package com.readassistant.feature.rss.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.readassistant.feature.rss.data.RssRepositoryImpl
import com.readassistant.feature.rss.domain.Feed
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FeedListViewModel @Inject constructor(private val repository: RssRepositoryImpl) : ViewModel() {
    val feeds: StateFlow<List<Feed>> = repository.getAllFeeds().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun addFeed(url: String) { viewModelScope.launch { _isLoading.value = true; _error.value = null; try { repository.addFeed(url) } catch (e: Exception) { _error.value = e.message }; _isLoading.value = false } }
    fun deleteFeed(feedId: Long) { viewModelScope.launch { repository.deleteFeed(feedId) } }
    fun refreshAll() { viewModelScope.launch { _isLoading.value = true; try { repository.refreshAllFeeds() } catch (_: Exception) {}; _isLoading.value = false } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedListScreen(onFeedClick: (Long) -> Unit, viewModel: FeedListViewModel = hiltViewModel()) {
    val feeds by viewModel.feeds.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    val pullRefreshState = rememberPullToRefreshState()
    if (pullRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            viewModel.refreshAll()
        }
    }
    LaunchedEffect(isLoading) {
        if (!isLoading) pullRefreshState.endRefresh()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Feeds") }) },
        floatingActionButton = { FloatingActionButton(onClick = { showAddDialog = true }) { Icon(Icons.Default.Add, contentDescription = "Add Feed") } }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(pullRefreshState.nestedScrollConnection)
        ) {
            if (feeds.isEmpty() && !isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.RssFeed, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(16.dp)); Text("No feeds yet", style = MaterialTheme.typography.titleMedium); Text("Tap + to add an RSS feed")
                    }
                }
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(feeds, key = { it.id }) { feed ->
                    ListItem(
                        headlineContent = { Text(feed.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(feed.description, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { Icon(Icons.Default.RssFeed, contentDescription = null) },
                        trailingContent = { Row(verticalAlignment = Alignment.CenterVertically) { if (feed.unreadCount > 0) { Badge { Text("${feed.unreadCount}") }; Spacer(Modifier.width(8.dp)) }; IconButton(onClick = { viewModel.deleteFeed(feed.id) }) { Icon(Icons.Default.Delete, contentDescription = "Delete") } } },
                        modifier = Modifier.clickable { onFeedClick(feed.id) }
                    )
                }
            }
            PullToRefreshContainer(
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
    if (showAddDialog) {
        var url by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { showAddDialog = false }, title = { Text("Add RSS Feed") },
            text = { OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("Feed URL") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { if (url.isNotBlank()) { viewModel.addFeed(url.trim()); showAddDialog = false } }, enabled = url.isNotBlank()) { Text("Add") } },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } })
    }
}
