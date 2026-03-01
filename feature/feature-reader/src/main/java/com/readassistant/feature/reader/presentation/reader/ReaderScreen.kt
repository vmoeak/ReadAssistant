package com.readassistant.feature.reader.presentation.reader

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.hilt.navigation.compose.hiltViewModel
import kotlin.math.roundToInt
import com.readassistant.core.domain.model.ContentType
import com.readassistant.core.domain.model.SelectionRect
import com.readassistant.core.ui.components.SelectionToolbar
import com.readassistant.core.ui.theme.*
import com.readassistant.feature.reader.presentation.renderer.NativeBookReader
import com.readassistant.feature.chat.presentation.ChatBottomSheet
import com.readassistant.feature.reader.presentation.renderer.WebViewReader
import com.readassistant.feature.reader.presentation.renderer.generateReaderCss
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
    var showChaptersList by remember { mutableStateOf(false) }
    var showTopBar by remember { mutableStateOf(false) }
    var webPageRenderReady by remember(uiState.contentType, uiState.contentId) { mutableStateOf(false) }
    var seekCommandId by remember { mutableStateOf(0) }
    var seekParagraphIndex by remember { mutableStateOf<Int?>(null) }
    var seekPageIndex by remember { mutableStateOf<Int?>(null) }
    var seekProgress by remember { mutableStateOf<Float?>(null) }
    var highlightParagraphIndex by remember { mutableStateOf<Int?>(null) }
    var paragraphToPageMap by remember(uiState.contentId) { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    var initialSeekApplied by remember(uiState.contentId) { mutableStateOf(false) }
    var sliderValue by remember { mutableStateOf(0f) }
    var sliderDragging by remember { mutableStateOf(false) }
    var lastSelectionRect by remember { mutableStateOf<SelectionRect?>(null) }
    var showOriginalPage by remember { mutableStateOf(false) }
    val isBook = isBookContentType(uiState.contentType)
    LaunchedEffect(uiState.contentType, uiState.contentId) {
        translationViewModel.clearTranslations()
        showOriginalPage = false
    }
    // Auto-open original page when extracted content is empty (e.g. JS-rendered sites)
    LaunchedEffect(uiState.isLoading, uiState.htmlContent) {
        if (!uiState.isLoading && !isBook && uiState.htmlContent.isBlank()
            && uiState.error == null && uiState.originalLink.isNotBlank()
            && !showOriginalPage) {
            showOriginalPage = true
        }
    }
    LaunchedEffect(highlightParagraphIndex) {
        if (highlightParagraphIndex != null) {
            kotlinx.coroutines.delay(2500)
            highlightParagraphIndex = null
        }
    }
    LaunchedEffect(uiState.textSelection?.rect) {
        uiState.textSelection?.rect?.let { lastSelectionRect = it }
    }
    LaunchedEffect(uiState.progressPercent, sliderDragging) {
        if (!sliderDragging) sliderValue = uiState.progressPercent.coerceIn(0f, 1f)
    }
    LaunchedEffect(isBook, uiState.contentId, uiState.bookParagraphs.size, uiState.progressPercent, uiState.savedBookPageIndex) {
        if (!isBook || uiState.bookParagraphs.isEmpty() || initialSeekApplied) return@LaunchedEffect
        initialSeekApplied = true
        val savedPageIndex = uiState.savedBookPageIndex
        if (savedPageIndex != null && savedPageIndex >= 0) {
            seekPageIndex = savedPageIndex
            seekParagraphIndex = null
            seekProgress = null
            seekCommandId += 1
        } else {
            val savedProgress = uiState.progressPercent.coerceIn(0f, 1f)
            if (savedProgress <= 0.001f) return@LaunchedEffect
            seekPageIndex = null
            seekParagraphIndex = null
            seekProgress = savedProgress
            seekCommandId += 1
        }
    }
    // webPageRenderReady is reset to false by the WebViewReader's update block
    // (via onPageRenderReady callback) when new content is loaded, so no need
    // for an additional LaunchedEffect reset here.
    val progressLabel = if (isBook && uiState.bookParagraphs.isNotEmpty()) {
        val percent = (uiState.progressPercent * 100f).coerceIn(0f, 100f).roundToInt()
        if (uiState.totalChapters > 1) {
            "${uiState.currentChapterIndex + 1} / ${uiState.totalChapters}  $percent%"
        } else {
            "$percent%"
        }
    } else null
    val readerBottomInset = 0.dp

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = rc.background
    ) { _ ->
        Box(Modifier.fillMaxSize()) {
            when {
                uiState.error != null -> Text(uiState.error!!, Modifier.align(Alignment.Center).padding(16.dp), color = MaterialTheme.colorScheme.error)
                !uiState.isLoading && !isBook && uiState.htmlContent.isBlank() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                    ) {
                        Text(
                            "Content could not be extracted",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (uiState.originalLink.isNotBlank()) {
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { showOriginalPage = true }) {
                                Text("View Original Page")
                            }
                        }
                    }
                }
                isBook -> NativeBookReader(
                    paragraphs = uiState.bookParagraphs,
                    themeType = rtt,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    isBilingualMode = isBilingual,
                    translations = translations,
                    initialPageIndex = uiState.savedBookPageIndex,
                    initialProgress = uiState.progressPercent,
                    seekCommandId = seekCommandId,
                    seekParagraphIndex = seekParagraphIndex,
                    seekPageIndex = seekPageIndex,
                    seekProgress = seekProgress,
                    onProgressChanged = { currentIdx, total, progress ->
                        viewModel.onBookPageChanged(currentIdx, total, progress)
                    },
                    onParagraphPageMapChanged = { map ->
                        paragraphToPageMap = map
                    },
                    onParagraphsVisible = { visible ->
                        translationViewModel.translateParagraphs(visible)
                    },
                    onSingleTap = {
                        if (uiState.showSelectionToolbar) {
                            viewModel.clearSelection()
                        } else {
                            showTopBar = !showTopBar
                        }
                    },
                    onLinkClicked = { targetParagraphIndex ->
                        seekPageIndex = null
                        seekProgress = null
                        seekParagraphIndex = targetParagraphIndex
                        seekCommandId += 1
                        highlightParagraphIndex = targetParagraphIndex
                    },
                    highlightParagraphIndex = highlightParagraphIndex,
                    onParagraphLongPress = { paragraphIndex, selectedText, rect ->
                        viewModel.onTextSelected(
                            com.readassistant.core.domain.model.TextSelection(
                                selectedText = selectedText,
                                paragraphIndex = paragraphIndex,
                                rect = rect
                            )
                        )
                    },
                    showSelectionToolbar = uiState.showSelectionToolbar,
                    isControlsVisible = showTopBar,
                    modifier = Modifier
                        .fillMaxSize()
                )
                else -> WebViewReader(
                    htmlContent = if (uiState.isLoading) "<p></p>" else uiState.htmlContent,
                    themeType = rtt, fontSize = fontSize,
                    lineHeight = lineHeight, isBilingualMode = isBilingual,
                    translations = translations,
                    onTextSelected = { viewModel.onTextSelected(it) },
                    onProgressChanged = { viewModel.saveProgress(it) },
                    onParagraphsExtracted = { paragraphs ->
                        translationViewModel.translateParagraphs(paragraphs)
                    },
                    onPageRenderReady = { ready -> webPageRenderReady = ready },
                    pagedMode = false,
                    onSwipeLeft = if (uiState.originalLink.isNotBlank()) {{ showOriginalPage = true }} else null,
                    onSwipeRight = null,
                    onPageChanged = null,
                    onChaptersExtracted = null,
                    seekCommandId = 0,
                    seekPage = null,
                    seekProgress = null,
                    onSingleTap = {
                        if (uiState.showSelectionToolbar) {
                            viewModel.clearSelection()
                        } else {
                            showTopBar = !showTopBar
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = readerBottomInset)
                )
            }
            val showLoadingOverlay = uiState.error == null && !isBook && (
                uiState.isLoading || (uiState.htmlContent.isNotBlank() && !webPageRenderReady)
            )
            if (showLoadingOverlay) {
                Surface(
                    color = rc.background,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = readerBottomInset)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
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
                    onNotesClick = {
                        showNotesList = true
                    },
                    progressText = null,
                    backgroundColor = rc.background
                )
            }
            if (isBook && showTopBar) {
                Surface(
                    color = rc.background,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = 4.dp, end = 4.dp, bottom = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .clickable(onClick = { showChaptersList = true }),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.List,
                                contentDescription = "Chapters",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Slider(
                            value = sliderValue,
                            onValueChange = {
                                sliderDragging = true
                                sliderValue = it.coerceIn(0f, 1f)
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp),
                            onValueChangeFinished = {
                                sliderDragging = false
                                seekPageIndex = null
                                seekParagraphIndex = null
                                seekProgress = sliderValue
                                seekCommandId += 1
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = rc.onBackground.copy(alpha = 0.9f),
                                activeTrackColor = rc.onBackground.copy(alpha = 0.55f),
                                inactiveTrackColor = rc.onBackground.copy(alpha = 0.22f)
                            )
                        )
                        Text(
                            text = "${(sliderValue * 100f).roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
            if (!progressLabel.isNullOrBlank()) {
                Text(
                    text = progressLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = 10.dp)
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
                modifier = if ((uiState.textSelection?.rect ?: lastSelectionRect) != null) {
                    val rect = uiState.textSelection?.rect ?: lastSelectionRect!!
                    val density = LocalDensity.current
                    Modifier.layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val gap = 48.dp.roundToPx()
                        val leftPx = rect.left * density.density
                        val rightPx = rect.right * density.density
                        val topPx = rect.top * density.density
                        val bottomPx = rect.bottom * density.density
                        
                        val centerX = ((leftPx + rightPx) / 2).toInt()
                        
                        var x = centerX - placeable.width / 2
                        // Try placing above the selection
                        var y = topPx.toInt() - placeable.height - gap
                        
                        // Clamp horizontally
                        x = x.coerceIn(0, (constraints.maxWidth - placeable.width).coerceAtLeast(0))
                        
                        // If truly no room above (not even half toolbar fits), place below
                        if (y < 0 && topPx.toInt() < placeable.height / 2) {
                            y = bottomPx.toInt() + gap
                        }
                        // Otherwise keep above, clamped to screen top
                        y = y.coerceAtLeast(0)
                        
                        // If still goes off-screen bottom, clamp
                        y = y.coerceIn(0, (constraints.maxHeight - placeable.height).coerceAtLeast(0))
                        
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

    // In-app original page viewer
    AnimatedVisibility(
        visible = showOriginalPage && uiState.originalLink.isNotBlank(),
        enter = slideInHorizontally(initialOffsetX = { it }),
        exit = slideOutHorizontally(targetOffsetX = { it })
    ) {
        OriginalPageViewer(
            url = uiState.originalLink,
            title = uiState.title,
            themeType = rtt,
            fontSize = fontSize,
            lineHeight = lineHeight,
            onBack = { showOriginalPage = false }
        )
    }
    if (showOriginalPage) {
        BackHandler { showOriginalPage = false }
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
    if (showChaptersList) {
        ModalBottomSheet(onDismissRequest = { showChaptersList = false }) {
            Text("Chapters", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                if (uiState.chapters.isEmpty()) {
                    item { Text("No chapter headings found.", modifier = Modifier.padding(vertical = 12.dp)) }
                } else {
                    items(uiState.chapters) { chapter ->
                        val mappedPage = paragraphToPageMap[chapter.pageIndex]
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    seekPageIndex = null
                                    seekProgress = null
                                    seekParagraphIndex = chapter.pageIndex
                                    seekCommandId += 1
                                    showChaptersList = false
                                }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(
                                text = chapter.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Page ${mappedPage?.plus(1) ?: "..."}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

private fun isBookContentType(type: ContentType): Boolean = when (type) {
    ContentType.RSS_ARTICLE, ContentType.WEB_ARTICLE -> false
    else -> true
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OriginalPageViewer(
    url: String,
    title: String,
    themeType: ReadingThemeType,
    fontSize: Float,
    lineHeight: Float,
    onBack: () -> Unit
) {
    var isBilingual by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val rc = when (themeType) {
        ReadingThemeType.LIGHT -> lightReaderColors
        ReadingThemeType.SEPIA -> sepiaReaderColors
        ReadingThemeType.DARK -> darkReaderColors
    }

    val translationVM: TranslationViewModel = hiltViewModel()
    val translations by translationVM.translations.collectAsState()
    val translationCss = remember(themeType, fontSize, lineHeight) {
        val css = generateReaderCss(themeType, fontSize, lineHeight)
        css.substringAfter(".translation{", "color:#555;").substringBefore("}")
    }

    // When bilingual mode is re-enabled, re-inject already cached translations.
    // data-ra-idx attributes persist across toggle cycles, so we can inject immediately
    // without waiting for paragraph extraction to run again.
    LaunchedEffect(isBilingual) {
        if (!isBilingual) return@LaunchedEffect
        val wv = webViewRef ?: return@LaunchedEffect
        translations.forEach { (idx, pair) ->
            val escaped = pair.translatedText
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
            wv.evaluateJavascript("""
                (function(){
                    var p=document.querySelector('[data-ra-idx="$idx"]');
                    if(!p)return;
                    if(!('$escaped').trim())return;
                    var t=p.nextElementSibling;
                    if(!t||!t.classList.contains('ra-translation')){
                        t=document.createElement('div');
                        t.className='ra-translation';
                        t.setAttribute("style","display:block;$translationCss");
                        p.parentNode.insertBefore(t,p.nextSibling);
                    }
                    t.textContent='$escaped';
                })();
            """.trimIndent(), null)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        isBilingual = !isBilingual
                        val wv = webViewRef ?: return@IconButton
                        if (isBilingual) {
                            // Inject translation extraction script
                            val css = generateReaderCss(themeType, fontSize, lineHeight)
                            val translationStyle = css.substringAfter(".translation{", "").substringBefore("}")
                            wv.evaluateJavascript("""
                                (function(){
                                    if(!document.getElementById('ra-trans-style')){
                                        var s=document.createElement('style');
                                        s.id='ra-trans-style';
                                        s.textContent='.ra-translation{display:block!important;clear:both;float:none;position:relative;width:100%;box-sizing:border-box;$translationStyle} .ra-translation:empty{display:none!important}';
                                        (document.head||document.documentElement).appendChild(s);
                                    }
                                    var skip='nav,header,footer,aside,.nav,.navbar,.menu,.sidebar,.breadcrumb,.header,.footer,[role=navigation],[role=banner],[role=contentinfo]';
                                    function isInSkip(el){var p=el;while(p){if(p.matches&&p.matches(skip))return true;p=p.parentElement;}return false;}
                                    function isLeafText(el){
                                        for(var c=0;c<el.children.length;c++){
                                            var child=el.children[c];
                                            var tag=child.tagName;
                                            if(tag==='BR'||tag==='IMG'||tag==='SVG'||tag==='A')continue;
                                            if((child.textContent||'').trim().length>=10)return false;
                                        }
                                        return true;
                                    }
                                    var scope=document.body;
                                    var all=scope.querySelectorAll('p,h1,h2,h3,h4,h5,h6,li,blockquote,div,span');
                                    var texts=[];
                                    var seenTexts=new Set();
                                    var idx=0;
                                    all.forEach(function(el){
                                        var t=(el.textContent||'').trim();
                                        if(t.length<10)return;
                                        if(isInSkip(el))return;
                                        if(!isLeafText(el))return;
                                        if(seenTexts.has(t))return;
                                        seenTexts.add(t);
                                        el.setAttribute('data-ra-idx',idx);
                                        texts.push(idx+'||'+t);
                                        idx++;
                                    });
                                    if(typeof Android!=='undefined'&&Android.onOriginalPageParagraphs){
                                        Android.onOriginalPageParagraphs(texts.join('@@SEP@@'));
                                    }
                                })();
                            """.trimIndent(), null)
                        } else {
                            wv.evaluateJavascript(
                                "document.querySelectorAll('.ra-translation').forEach(function(e){e.remove()});",
                                null
                            )
                        }
                    }) {
                        Icon(
                            Icons.Default.Translate,
                            contentDescription = "Translate",
                            tint = if (isBilingual) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = rc.background),
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        },
        containerColor = rc.background
    ) { padding ->
        // Apply translations as they arrive from the ViewModel
        LaunchedEffect(translations) {
            val wv = webViewRef ?: return@LaunchedEffect
            if (!isBilingual) return@LaunchedEffect
            translations.forEach { (idx, pair) ->
                val escaped = pair.translatedText
                    .replace("\\", "\\\\")
                    .replace("'", "\\'")
                    .replace("\n", "\\n")
                wv.evaluateJavascript("""
                    (function(){
                        var p=document.querySelector('[data-ra-idx="$idx"]');
                        if(!p)return;
                        if(!('$escaped').trim())return;
                        var t=p.nextElementSibling;
                        if(!t||!t.classList.contains('ra-translation')){
                            t=document.createElement('div');
                            t.className='ra-translation';
                            t.setAttribute("style","display:block;$translationCss");
                            p.parentNode.insertBefore(t,p.nextSibling);
                        }
                        t.textContent='$escaped';
                    })();
                """.trimIndent(), null)
            }
        }

        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    webViewClient = WebViewClient()
                    addJavascriptInterface(object {
                        @android.webkit.JavascriptInterface
                        fun onOriginalPageParagraphs(data: String) {
                            if (data.isBlank()) return
                            val pairs = data.split("@@SEP@@").mapNotNull { entry ->
                                val parts = entry.split("||", limit = 2)
                                if (parts.size == 2) parts[0].toIntOrNull()?.let { it to parts[1] } else null
                            }
                            translationVM.translateParagraphs(pairs)
                        }
                    }, "Android")
                    loadUrl(url)
                    webViewRef = this
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }
}
