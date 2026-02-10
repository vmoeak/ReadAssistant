package com.readassistant.feature.reader.presentation.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.readassistant.core.domain.model.ContentType
import com.readassistant.core.ui.components.SelectionToolbar
import com.readassistant.core.ui.theme.*
import com.readassistant.feature.chat.presentation.ChatBottomSheet
import com.readassistant.feature.reader.presentation.renderer.WebViewReader
import com.readassistant.feature.reader.presentation.toolbar.ReaderTopBar
import com.readassistant.feature.reader.presentation.toolbar.SettingsPanel
import com.readassistant.feature.translation.presentation.TranslationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    onBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
    translationViewModel: TranslationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val themeType by viewModel.themeType.collectAsState(initial = "LIGHT")
    val fontSize by viewModel.fontSize.collectAsState(initial = 16f)
    val lineHeight by viewModel.lineHeight.collectAsState(initial = 1.75f)
    val rtt = try { ReadingThemeType.valueOf(themeType) } catch (_: Exception) { ReadingThemeType.LIGHT }
    val rc = when (rtt) { ReadingThemeType.LIGHT -> lightReaderColors; ReadingThemeType.SEPIA -> sepiaReaderColors; ReadingThemeType.DARK -> darkReaderColors }

    val isBilingual by translationViewModel.isBilingualMode.collectAsState()
    val translations by translationViewModel.translations.collectAsState()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showNoteDialog by remember { mutableStateOf(false) }
    var showNotesList by remember { mutableStateOf(false) }
    var showTopBar by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = rc.background
    ) { _ ->
        Box(Modifier.fillMaxSize()) {
            when {
                uiState.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                uiState.error != null -> Text(uiState.error!!, Modifier.align(Alignment.Center).padding(16.dp), color = MaterialTheme.colorScheme.error)
                else -> WebViewReader(
                    htmlContent = uiState.htmlContent, themeType = rtt, fontSize = fontSize,
                    lineHeight = lineHeight, isBilingualMode = isBilingual,
                    translations = translations,
                    onTextSelected = { viewModel.onTextSelected(it) },
                    onProgressChanged = { viewModel.saveProgress(it) },
                    onParagraphsExtracted = { paragraphs ->
                        translationViewModel.translateParagraphs(paragraphs)
                    },
                    pagedMode = isBookContentType(uiState.contentType),
                    onSwipeLeft = if (isBookContentType(uiState.contentType) && uiState.totalChapters > 1) ({ viewModel.nextBookPage() }) else null,
                    onSwipeRight = if (isBookContentType(uiState.contentType) && uiState.totalChapters > 1) ({ viewModel.prevBookPage() }) else null,
                    onSingleTap = { showTopBar = !showTopBar },
                    modifier = Modifier.fillMaxSize()
                )
            }
            AnimatedVisibility(
                visible = showTopBar,
                enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { -it / 2 }),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
            ) {
                ReaderTopBar(
                    title = uiState.title, onBack = onBack,
                    isBilingualMode = isBilingual,
                    onToggleTranslation = { translationViewModel.toggleBilingualMode() },
                    onSettingsClick = { viewModel.toggleSettingsPanel() },
                    onNotesClick = { showNotesList = true },
                    progressText = if (isBookContentType(uiState.contentType) && uiState.totalChapters > 1) {
                        "${uiState.currentChapterIndex + 1} / ${uiState.totalChapters}"
                    } else null
                )
            }
            SelectionToolbar(
                visible = uiState.showSelectionToolbar,
                onHighlight = {
                    showNoteDialog = true
                },
                onCopy = {
                    val text = uiState.textSelection?.selectedText ?: ""
                    if (text.isNotEmpty()) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Selected text", text))
                    }
                    viewModel.clearSelection()
                    scope.launch { snackbarHostState.showSnackbar("Copied to clipboard") }
                },
                onAskAi = { viewModel.showChatSheet() },
                onDismiss = { viewModel.clearSelection() },
                modifier = if (uiState.textSelection?.rect != null) {
                    val rect = uiState.textSelection!!.rect!!
                    val density = LocalDensity.current
                    Modifier.layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val topPadding = 16.dp.roundToPx()
                        val leftPx = rect.left * density.density
                        val rightPx = rect.right * density.density
                        val topPx = rect.top * density.density
                        val bottomPx = rect.bottom * density.density
                        
                        val centerX = ((leftPx + rightPx) / 2).toInt()
                        val topY = topPx.toInt()
                        val bottomY = bottomPx.toInt()
                        
                        var x = centerX - placeable.width / 2
                        var y = topY - placeable.height - topPadding
                        
                        // Clamp horizontally
                        x = x.coerceIn(0, constraints.maxWidth - placeable.width)
                        
                        // Flip vertically if too close to top
                        if (y < 0) {
                            y = bottomY + topPadding
                        }
                        
                        layout(placeable.width, placeable.height) {
                            placeable.place(x, y)
                        }
                    }
                } else {
                    Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
                }
            )
            if (uiState.showSettingsPanel) SettingsPanel(currentTheme = rtt, fontSize = fontSize, lineHeight = lineHeight, onThemeChange = { viewModel.updateTheme(it.name) }, onFontSizeChange = { viewModel.updateFontSize(it) }, onLineHeightChange = { viewModel.updateLineHeight(it) }, onDismiss = { viewModel.toggleSettingsPanel() }, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    // Show ChatBottomSheet when Ask AI is triggered
    if (uiState.showChatSheet) {
        ChatBottomSheet(
            quotedText = uiState.textSelection?.selectedText ?: "",
            onDismiss = { viewModel.hideChatSheet() }
        )
    }

    if (showNoteDialog) {
        var noteText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNoteDialog = false },
            title = { Text("Add Note") },
            text = { OutlinedTextField(value = noteText, onValueChange = { noteText = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.addNote(noteText)
                    showNoteDialog = false
                    scope.launch { snackbarHostState.showSnackbar("Note saved") }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showNoteDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showNotesList) {
        ModalBottomSheet(onDismissRequest = { showNotesList = false }) {
            Text("Notes", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
            LazyColumn(contentPadding = PaddingValues(16.dp)) {
                items(uiState.notes) { item ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Box(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                val highlight = item.highlight
                                if (highlight != null) {
                                    Text(
                                        text = "\"${highlight.selectedText}\"",
                                        style = MaterialTheme.typography.bodySmall.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                                        color = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.padding(bottom = 8.dp, end = 24.dp)
                                    )
                                }
                                Text(item.note.noteText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 24.dp))
                                Text("Saved on: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(java.util.Date(item.note.createdAt))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                            }
                            IconButton(onClick = { viewModel.deleteNote(item) }, modifier = Modifier.align(Alignment.TopEnd)) {
                                Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                if (uiState.notes.isEmpty()) item { Text("No notes yet.") }
            }
        }
    }
}

private fun isBookContentType(type: ContentType): Boolean = when (type) {
    ContentType.RSS_ARTICLE, ContentType.WEB_ARTICLE -> false
    else -> true
}
