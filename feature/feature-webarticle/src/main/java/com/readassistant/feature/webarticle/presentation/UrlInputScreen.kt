package com.readassistant.feature.webarticle.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.readassistant.core.data.db.dao.WebArticleDao
import com.readassistant.core.data.db.entity.WebArticleEntity
import com.readassistant.feature.webarticle.data.extractor.ArticleExtractor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UrlInputViewModel @Inject constructor(private val extractor: ArticleExtractor, private val dao: WebArticleDao) : ViewModel() {
    private val _state = MutableStateFlow<ExtractState>(ExtractState.Idle)
    val state: StateFlow<ExtractState> = _state
    fun extractArticle(url: String) { viewModelScope.launch {
        _state.value = ExtractState.Loading
        try {
            val normalizedUrl = if (!url.matches(Regex("^https?://.*", RegexOption.IGNORE_CASE))) "https://$url" else url
            val existing = dao.getArticleByUrl(normalizedUrl); if (existing != null) { _state.value = ExtractState.Success(existing.id); return@launch }
            val r = extractor.extract(normalizedUrl)
            val id = dao.insert(WebArticleEntity(url = normalizedUrl, title = r.title, content = r.content, textContent = r.textContent, imageUrl = r.imageUrl, siteName = r.siteName))
            _state.value = ExtractState.Success(id)
        } catch (e: java.net.UnknownHostException) { _state.value = ExtractState.Error("Cannot resolve host. Check URL or network.")
        } catch (e: java.net.SocketTimeoutException) { _state.value = ExtractState.Error("Connection timed out. Try again.")
        } catch (e: org.jsoup.HttpStatusException) { _state.value = ExtractState.Error("HTTP error ${e.statusCode}: ${e.url}")
        } catch (e: Exception) { _state.value = ExtractState.Error(e.message ?: "Extraction failed") }
    }}
    sealed class ExtractState { data object Idle : ExtractState(); data object Loading : ExtractState(); data class Success(val id: Long) : ExtractState(); data class Error(val msg: String) : ExtractState() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UrlInputScreen(onArticleSaved: (Long) -> Unit, onBack: () -> Unit, viewModel: UrlInputViewModel = hiltViewModel()) {
    var url by remember { mutableStateOf("") }; val state by viewModel.state.collectAsState(); val clip = LocalClipboardManager.current
    LaunchedEffect(state) { if (state is UrlInputViewModel.ExtractState.Success) onArticleSaved((state as UrlInputViewModel.ExtractState.Success).id) }
    Scaffold(topBar = { TopAppBar(title = { Text("Add Article") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Default.Link, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(24.dp))
            Text("Extract Article from URL", style = MaterialTheme.typography.headlineSmall); Spacer(Modifier.height(24.dp))
            OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("URL") }, singleLine = true, modifier = Modifier.fillMaxWidth(), trailingIcon = { IconButton(onClick = { clip.getText()?.text?.let { url = it } }) { Icon(Icons.Default.ContentPaste, "Paste") } })
            Spacer(Modifier.height(16.dp))
            Button(onClick = { if (url.isNotBlank()) viewModel.extractArticle(url.trim()) }, enabled = url.isNotBlank() && state !is UrlInputViewModel.ExtractState.Loading, modifier = Modifier.fillMaxWidth()) { if (state is UrlInputViewModel.ExtractState.Loading) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }; Text("Extract & Read") }
            if (state is UrlInputViewModel.ExtractState.Error) { Spacer(Modifier.height(16.dp)); Text((state as UrlInputViewModel.ExtractState.Error).msg, color = MaterialTheme.colorScheme.error) }
        }
    }
}
