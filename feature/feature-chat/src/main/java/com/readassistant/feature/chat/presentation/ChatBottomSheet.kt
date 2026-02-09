package com.readassistant.feature.chat.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.readassistant.feature.chat.presentation.components.ChatBubble
import com.readassistant.feature.chat.presentation.components.ChatInput
import com.readassistant.feature.chat.presentation.components.QuotedTextCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatBottomSheet(
    quotedText: String,
    onDismiss: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(quotedText) {
        viewModel.sendIntent(ChatIntent.InitWithSelection(quotedText))
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.fillMaxHeight(0.7f)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Ask AI", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
            HorizontalDivider()

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                if (uiState.quotedText.isNotBlank()) {
                    item { QuotedTextCard(text = uiState.quotedText) }
                }
                items(uiState.messages) { message ->
                    ChatBubble(message = message)
                }
            }

            if (uiState.error != null) {
                Text(
                    text = uiState.error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            ChatInput(
                text = uiState.inputText,
                isStreaming = uiState.isStreaming,
                onTextChange = { viewModel.sendIntent(ChatIntent.UpdateInput(it)) },
                onSend = { viewModel.sendIntent(ChatIntent.SendMessage(uiState.inputText)) },
                onStop = { viewModel.sendIntent(ChatIntent.CancelStreaming) }
            )
        }
    }
}
