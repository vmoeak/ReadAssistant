package com.readassistant.feature.reader.presentation.toolbar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp

@Composable
fun ReaderTopBar(
    title: String,
    onBack: () -> Unit,
    isBilingualMode: Boolean,
    onToggleTranslation: () -> Unit,
    onSettingsClick: () -> Unit,
    onNotesClick: () -> Unit,
    progressText: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(44.dp)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                CompactActionIcon(
                    icon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") },
                    onClick = onBack
                )
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 18.sp),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp, end = 6.dp)
                )
                if (!progressText.isNullOrBlank()) {
                    Text(
                        text = progressText,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
                CompactActionIcon(
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Translate,
                            contentDescription = "Translation",
                            tint = if (isBilingualMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = onToggleTranslation
                )
                CompactActionIcon(
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Notes") },
                    onClick = onNotesClick
                )
                CompactActionIcon(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    onClick = onSettingsClick
                )
            }
        }
    }
}

@Composable
private fun CompactActionIcon(
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(
            LocalContentColor provides Color.Unspecified
        ) {
            icon()
        }
    }
}
