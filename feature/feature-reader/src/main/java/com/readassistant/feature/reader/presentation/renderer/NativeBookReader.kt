package com.readassistant.feature.reader.presentation.renderer

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.readassistant.core.ui.theme.ReadingThemeType
import com.readassistant.feature.reader.presentation.reader.ReaderParagraph
import com.readassistant.feature.translation.domain.TranslationPair
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt

@Composable
fun NativeBookReader(
    paragraphs: List<ReaderParagraph>,
    themeType: ReadingThemeType,
    fontSize: Float,
    lineHeight: Float,
    isBilingualMode: Boolean,
    translations: Map<Int, TranslationPair>,
    seekCommandId: Int = 0,
    seekParagraphIndex: Int? = null,
    seekProgress: Float? = null,
    onProgressChanged: (currentIndex: Int, totalItems: Int, progress: Float) -> Unit,
    onParagraphsVisible: (List<Pair<Int, String>>) -> Unit,
    onSingleTap: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val textColor = when (themeType) {
        ReadingThemeType.LIGHT -> MaterialTheme.colorScheme.onSurface
        ReadingThemeType.SEPIA -> MaterialTheme.colorScheme.onSurface
        ReadingThemeType.DARK -> MaterialTheme.colorScheme.onSurface
    }
    val paragraphStyle = MaterialTheme.typography.bodyLarge.copy(
        fontSize = fontSize.sp,
        lineHeight = (fontSize * lineHeight).sp,
        color = textColor
    )
    val headingStyle = MaterialTheme.typography.headlineSmall.copy(
        fontSize = (fontSize + 8f).sp,
        lineHeight = ((fontSize + 8f) * 1.2f).sp,
        color = textColor
    )

    LaunchedEffect(paragraphs) {
        if (paragraphs.isEmpty()) {
            onProgressChanged(0, 1, 0f)
        } else {
            onProgressChanged(0, paragraphs.size, 0f)
        }
    }

    LaunchedEffect(isBilingualMode, paragraphs) {
        if (!isBilingualMode || paragraphs.isEmpty()) return@LaunchedEffect
        onParagraphsVisible(
            paragraphs.take(60).map { it.index to it.text }
        )
    }

    LaunchedEffect(listState, paragraphs, isBilingualMode) {
        snapshotFlow {
            val first = listState.firstVisibleItemIndex
            val total = paragraphs.size.coerceAtLeast(1)
            val progress = if (total <= 1) 1f else first.toFloat() / (total - 1).toFloat()
            val visible = listState.layoutInfo.visibleItemsInfo.map { it.index }.distinct()
            Triple(first, progress, visible)
        }.distinctUntilChanged().collect { (firstIndexRaw, progressRaw, visibleRaw) ->
            val maxIndex = (paragraphs.size - 1).coerceAtLeast(0)
            val firstIndex = firstIndexRaw.coerceIn(0, maxIndex)
            val progress = progressRaw.coerceIn(0f, 1f)
            onProgressChanged(firstIndex, paragraphs.size.coerceAtLeast(1), progress)
            if (isBilingualMode && visibleRaw.isNotEmpty()) {
                val visiblePairs = visibleRaw.mapNotNull { idx ->
                    paragraphs.getOrNull(idx)?.let { it.index to it.text }
                }
                if (visiblePairs.isNotEmpty()) onParagraphsVisible(visiblePairs)
            }
        }
    }

    LaunchedEffect(seekCommandId, seekParagraphIndex, seekProgress, paragraphs.size) {
        if (seekCommandId == 0 || paragraphs.isEmpty()) return@LaunchedEffect
        when {
            seekParagraphIndex != null -> {
                val target = seekParagraphIndex.coerceIn(0, paragraphs.lastIndex)
                listState.scrollToItem(target)
            }
            seekProgress != null -> {
                val progress = seekProgress.coerceIn(0f, 1f)
                val target = (progress * paragraphs.lastIndex.toFloat()).roundToInt()
                listState.scrollToItem(target.coerceIn(0, paragraphs.lastIndex))
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.pointerInput(onSingleTap) {
            detectTapGestures(
                onTap = { onSingleTap?.invoke() }
            )
        }
    ) {
        items(items = paragraphs, key = { it.index }) { paragraph ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = if (paragraph.isHeading) 10.dp else 6.dp)
            ) {
                Text(
                    text = paragraph.text,
                    style = if (paragraph.isHeading) headingStyle else paragraphStyle
                )
                if (isBilingualMode) {
                    val translated = translations[paragraph.index]?.translatedText.orEmpty()
                    if (translated.isNotBlank()) {
                        Text(
                            text = translated,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = (fontSize - 1f).coerceAtLeast(12f).sp,
                                lineHeight = ((fontSize - 1f).coerceAtLeast(12f) * lineHeight).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(96.dp))
        }
    }
}
