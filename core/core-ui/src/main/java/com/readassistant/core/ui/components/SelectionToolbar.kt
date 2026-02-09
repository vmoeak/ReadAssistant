package com.readassistant.core.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

@Composable
fun SelectionToolbar(
    offset: IntOffset,
    onHighlight: () -> Unit,
    onCopy: () -> Unit,
    onNote: () -> Unit,
    onAskAi: () -> Unit,
    onDismiss: () -> Unit
) {
    Popup(
        offset = offset,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = false)
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            shadowElevation = 4.dp,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp
        ) {
            Row(
                modifier = Modifier.padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(onClick = onHighlight) {
                    Icon(Icons.Default.Edit, contentDescription = "Highlight")
                }
                IconButton(onClick = onCopy) {
                    Icon(Icons.Default.Share, contentDescription = "Copy")
                }
                IconButton(onClick = onNote) {
                    Icon(Icons.Default.Create, contentDescription = "Note")
                }
                IconButton(onClick = onAskAi) {
                    Icon(Icons.Default.Info, contentDescription = "Ask AI")
                }
            }
        }
    }
}
