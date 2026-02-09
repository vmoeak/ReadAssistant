package com.readassistant.feature.reader.presentation.reader

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.readassistant.core.ui.components.SelectionToolbar
import com.readassistant.core.ui.theme.*
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

    Scaffold(
        topBar = {
            ReaderTopBar(
                title = uiState.title, onBack = onBack,
                isBilingualMode = isBilingual,
                onToggleTranslation = { translationViewModel.toggleBilingualMode() },
                onSettingsClick = { viewModel.toggleSettingsPanel() }
            )
        },
        containerColor = rc.background
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
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
                    modifier = Modifier.fillMaxSize()
                )
            }
            SelectionToolbar(visible = uiState.showSelectionToolbar, onHighlight = { viewModel.addHighlight() }, onCopy = { viewModel.clearSelection() }, onNote = { viewModel.clearSelection() }, onAskAi = { viewModel.showChatSheet() }, onDismiss = { viewModel.clearSelection() }, modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
            if (uiState.showSettingsPanel) SettingsPanel(currentTheme = rtt, fontSize = fontSize, lineHeight = lineHeight, onThemeChange = { viewModel.updateTheme(it.name) }, onFontSizeChange = { viewModel.updateFontSize(it) }, onLineHeightChange = { viewModel.updateLineHeight(it) }, onDismiss = { viewModel.toggleSettingsPanel() }, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}
