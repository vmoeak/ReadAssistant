package com.readassistant.feature.chat.presentation.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun StreamingTextView(
    text: String,
    isStreaming: Boolean,
    modifier: Modifier = Modifier
) {
    Text(
        text = if (isStreaming) "$text ▌" else text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier
    )
}
