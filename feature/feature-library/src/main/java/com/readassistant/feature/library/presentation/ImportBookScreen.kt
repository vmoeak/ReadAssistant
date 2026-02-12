package com.readassistant.feature.library.presentation

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.readassistant.core.data.db.dao.BookDao
import com.readassistant.core.data.db.entity.BookEntity
import com.readassistant.feature.library.data.parser.BookParserFactory
import com.readassistant.feature.library.data.reading.BookReadingRepository
import com.readassistant.feature.library.domain.BookFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ImportBookViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: BookDao,
    private val parserFactory: BookParserFactory,
    private val bookReadingRepository: BookReadingRepository
) : ViewModel() {
    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _state

    private fun queryFileName(uri: Uri): String? {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    fun importBook(uri: Uri) {
        viewModelScope.launch {
            _state.value = ImportState.Loading
            runCatching {
                withContext(Dispatchers.IO) {
                    val name = queryFileName(uri)
                        ?: uri.lastPathSegment?.substringAfterLast("/")
                        ?: "unknown"
                    val ext = name.substringAfterLast(".", "")
                    val mimeType = context.contentResolver.getType(uri)
                    val format = BookFormat.fromExtension(ext)
                        ?: mimeType?.let { BookFormat.fromMimeType(it) }
                        ?: error("Unsupported format: .$ext")

                    val dir = File(context.filesDir, "books/${System.currentTimeMillis()}").apply { mkdirs() }
                    val dest = File(dir, name)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        dest.outputStream().use { output -> input.copyTo(output) }
                    } ?: error("Failed to open selected file")

                    val parser = parserFactory.getParser(format)
                    val meta = parser.parseMetadata(dest.absolutePath)
                    val fileSize = dest.length()
                    val id = bookDao.insert(
                        BookEntity(
                            filePath = dest.absolutePath,
                            format = format.name,
                            title = meta.title,
                            author = meta.author,
                            coverPath = meta.coverPath,
                            totalChapters = meta.totalChapters,
                            fileSize = fileSize
                        )
                    )

                    // Do not block import on heavy prewarm for large books.
                    viewModelScope.launch(Dispatchers.IO) {
                        runCatching { bookReadingRepository.prewarm(id) }
                    }
                    id
                }
            }.onSuccess { id ->
                _state.value = ImportState.Success(id)
            }.onFailure { e ->
                _state.value = ImportState.Error(e.message ?: "Import failed")
            }
        }
    }

    sealed class ImportState {
        data object Idle : ImportState()
        data object Loading : ImportState()
        data class Success(val bookId: Long) : ImportState()
        data class Error(val message: String) : ImportState()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportBookScreen(onBookImported: (Long) -> Unit, onBack: () -> Unit, viewModel: ImportBookViewModel = hiltViewModel()) {
    val state by viewModel.importState.collectAsState()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { viewModel.importBook(it) } }
    LaunchedEffect(state) { if (state is ImportBookViewModel.ImportState.Success) onBookImported((state as ImportBookViewModel.ImportState.Success).bookId) }
    Scaffold(topBar = { TopAppBar(title = { Text("Import Book") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Default.FileOpen, null, Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(24.dp))
            Text("Import a Book", style = MaterialTheme.typography.headlineSmall); Spacer(Modifier.height(8.dp))
            Text("Supported: EPUB, PDF, MOBI, AZW3, FB2, TXT, HTML, CBZ, CBR", style = MaterialTheme.typography.bodyMedium); Spacer(Modifier.height(32.dp))
            Button(onClick = { launcher.launch(arrayOf("*/*")) }) { Text("Choose File") }
            when (state) {
                is ImportBookViewModel.ImportState.Loading -> { Spacer(Modifier.height(16.dp)); CircularProgressIndicator() }
                is ImportBookViewModel.ImportState.Error -> { Spacer(Modifier.height(16.dp)); Text((state as ImportBookViewModel.ImportState.Error).message, color = MaterialTheme.colorScheme.error) }
                else -> {}
            }
        }
    }
}
