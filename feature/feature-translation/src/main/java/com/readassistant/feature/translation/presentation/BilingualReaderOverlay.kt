package com.readassistant.feature.translation.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.readassistant.core.ui.components.BilingualParagraph
import com.readassistant.feature.translation.domain.TranslationPair

@Composable
fun BilingualReaderOverlay(paragraphs: List<Pair<Int, String>>, translations: Map<Int, TranslationPair>, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        items(paragraphs) { (i, t) -> BilingualParagraph(originalText = t, translatedText = translations[i]?.translatedText ?: "", isStreaming = translations[i]?.isComplete == false) }
    }
}
