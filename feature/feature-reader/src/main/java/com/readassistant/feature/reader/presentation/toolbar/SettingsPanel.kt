package com.readassistant.feature.reader.presentation.toolbar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.readassistant.core.ui.components.FontSettings
import com.readassistant.core.ui.components.ThemePicker
import com.readassistant.core.ui.theme.ReadingThemeType

@Composable
fun SettingsPanel(currentTheme: ReadingThemeType, fontSize: Float, lineHeight: Float, onThemeChange: (ReadingThemeType) -> Unit, onFontSizeChange: (Float) -> Unit, onLineHeightChange: (Float) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp), shadowElevation = 16.dp, tonalElevation = 4.dp) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Text("Reading Settings", style = MaterialTheme.typography.titleMedium)
            ThemePicker(currentTheme = currentTheme, onThemeSelected = onThemeChange)
            FontSettings(fontSize = fontSize, lineHeight = lineHeight, onFontSizeChange = onFontSizeChange, onLineHeightChange = onLineHeightChange)
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }
    }
}
