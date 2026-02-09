package com.readassistant.feature.reader.presentation.toolbar

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.readassistant.core.ui.components.SelectionToolbar as CoreSelectionToolbar

@Composable
fun ReaderSelectionToolbar(
    visible: Boolean,
    onHighlight: () -> Unit,
    onCopy: () -> Unit,
    onNote: () -> Unit,
    onAskAi: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    CoreSelectionToolbar(
        visible = visible,
        onHighlight = onHighlight,
        onCopy = onCopy,
        onNote = onNote,
        onAskAi = onAskAi,
        onDismiss = onDismiss,
        modifier = modifier
    )
}
