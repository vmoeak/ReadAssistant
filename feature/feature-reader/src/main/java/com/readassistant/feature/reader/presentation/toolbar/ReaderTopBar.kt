package com.readassistant.feature.reader.presentation.toolbar

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderTopBar(title: String, onBack: () -> Unit, isBilingualMode: Boolean, onToggleTranslation: () -> Unit, onSettingsClick: () -> Unit) {
    TopAppBar(title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        actions = { IconButton(onClick = onToggleTranslation) { Icon(Icons.Default.Translate, "Translation", tint = if (isBilingualMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }; IconButton(onClick = onSettingsClick) { Icon(Icons.Default.Settings, "Settings") } })
}
