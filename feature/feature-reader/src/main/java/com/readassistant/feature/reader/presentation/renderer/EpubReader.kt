package com.readassistant.feature.reader.presentation.renderer

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * EPUB reader using Readium EpubNavigatorFragment embedded in Compose.
 * Full implementation requires Readium kotlin-toolkit.
 * The fragment would be embedded via AndroidView + FragmentContainerView.
 */
@Composable
fun EpubReader(
    filePath: String,
    chapterIndex: Int,
    onTextSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "EPUB Reader",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Readium integration required for full EPUB rendering",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
