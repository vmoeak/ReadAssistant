package com.readassistant.feature.reader.presentation.toolbar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ReaderTopBar(
    title: String,
    onBack: () -> Unit,
    isBilingualMode: Boolean,
    onToggleTranslation: () -> Unit,
    onSettingsClick: () -> Unit,
    onNotesClick: () -> Unit,
    progressText: String? = null,
    backgroundColor: Color = MaterialTheme.colorScheme.surface
) {
    Surface(
        color = backgroundColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            CompactIcon(
                onClick = onBack,
                content = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(20.dp)) }
            )
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
            )
            if (!progressText.isNullOrBlank()) {
                Text(
                    text = progressText,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
            CompactIcon(
                onClick = onToggleTranslation,
                content = {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = "Translation",
                        tint = if (isBilingualMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )
            CompactIcon(
                onClick = onNotesClick,
                content = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Notes", modifier = Modifier.size(20.dp)) }
            )
            CompactIcon(
                onClick = onSettingsClick,
                content = { Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(20.dp)) }
            )
        }
    }
}

@Composable
private fun CompactIcon(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
