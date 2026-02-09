package com.readassistant.feature.translation.presentation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable

@Composable
fun TranslationToggleButton(isBilingualMode: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(Icons.Default.Translate, "Toggle Translation", tint = if (isBilingualMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
}
