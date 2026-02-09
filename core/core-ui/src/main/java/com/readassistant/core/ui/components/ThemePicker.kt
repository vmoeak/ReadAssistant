package com.readassistant.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.readassistant.core.ui.theme.ReadingThemeType

private data class ThemeOption(
    val type: ReadingThemeType,
    val name: String,
    val backgroundColor: Color,
    val textColor: Color
)

private val themeOptions = listOf(
    ThemeOption(ReadingThemeType.LIGHT, "Light", Color.White, Color.Black),
    ThemeOption(ReadingThemeType.SEPIA, "Sepia", Color(0xFFFBF0D9), Color(0xFF5B4636)),
    ThemeOption(ReadingThemeType.DARK, "Dark", Color(0xFF1A1A2E), Color(0xFFE0E0E0))
)

@Composable
fun ThemePicker(
    currentTheme: ReadingThemeType,
    onThemeSelected: (ReadingThemeType) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        themeOptions.forEach { theme ->
            val isSelected = theme.type == currentTheme
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onThemeSelected(theme.type) }
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(theme.backgroundColor)
                        .then(
                            if (isSelected)
                                Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            else Modifier.border(1.dp, Color.Gray.copy(alpha = 0.3f), CircleShape)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("A", color = theme.textColor, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(theme.name, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
