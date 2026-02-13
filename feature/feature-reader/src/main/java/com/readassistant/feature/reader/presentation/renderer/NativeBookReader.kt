package com.readassistant.feature.reader.presentation.renderer

import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.readassistant.core.domain.model.SelectionRect
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.readassistant.core.ui.theme.ReadingThemeType
import com.readassistant.core.ui.theme.darkReaderColors
import com.readassistant.core.ui.theme.lightReaderColors
import com.readassistant.core.ui.theme.sepiaReaderColors
import com.readassistant.feature.reader.presentation.reader.ReaderParagraph
import com.readassistant.feature.translation.domain.TranslationPair
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import java.io.File
import java.net.URLDecoder
import java.util.Base64
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
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
    seekPageIndex: Int? = null,
    seekProgress: Float? = null,
    onProgressChanged: (currentIndex: Int, totalItems: Int, progress: Float) -> Unit,
    onParagraphPageMapChanged: ((Map<Int, Int>) -> Unit)? = null,
    onParagraphsVisible: (List<Pair<Int, String>>) -> Unit,
    onSingleTap: (() -> Unit)? = null,
    onLinkClicked: ((paragraphIndex: Int) -> Unit)? = null,
    highlightParagraphIndex: Int? = null,
    onParagraphLongPress: ((paragraphIndex: Int, selectedText: String, rect: SelectionRect) -> Unit)? = null,
    showSelectionToolbar: Boolean = false,
    isControlsVisible: Boolean = false,
    modifier: Modifier = Modifier
) {
    val readerColors = when (themeType) {
        ReadingThemeType.LIGHT -> lightReaderColors
        ReadingThemeType.SEPIA -> sepiaReaderColors
        ReadingThemeType.DARK -> darkReaderColors
    }
    val textColor = readerColors.onBackground
    val secondaryTextColor = readerColors.onBackground.copy(alpha = 0.72f)
    val linkHighlightColor = Color(0xFF5B8DEF).copy(alpha = 0.25f)
    val selectionHighlightColor = Color(0xFFFFA726).copy(alpha = 0.35f)
    val density = LocalDensity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var lastHandledSeekCommandId by rememberSaveable { mutableIntStateOf(0) }
    // Character-level text selection state
    var selectionState by remember { mutableStateOf<NativeTextSelection?>(null) }

    // Clear internal selection when toolbar is dismissed externally (e.g. toolbar button actions)
    LaunchedEffect(showSelectionToolbar) {
        if (!showSelectionToolbar) {
            selectionState = null
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val statusBarTopPadding = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
        val navBarBottomPadding = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
        val topPadding = statusBarTopPadding + 16.dp
        val bottomPadding = navBarBottomPadding + 20.dp

        val topContentPaddingPx = with(density) { topPadding.toPx() }
        val bottomContentPaddingPx = with(density) { bottomPadding.toPx() }
        val paragraphGapPx = with(density) { 4.dp.toPx() }
        val viewportWidthPx = with(density) { maxWidth.toPx() }
        val viewportHeightPx = with(density) { maxHeight.toPx() }
        val horizontalPaddingPx = with(density) { 24.dp.toPx() * 2f }
        val contentWidthPx = (viewportWidthPx - horizontalPaddingPx).coerceAtLeast(220f)
        val contentHeightPx = (viewportHeightPx - topContentPaddingPx - bottomContentPaddingPx).coerceAtLeast(300f)
        val maxImageHeightPx = (contentHeightPx * 0.72f).coerceAtLeast(with(density) { 200.dp.toPx() })
        val maxImageHeightDp = with(density) { maxImageHeightPx.toDp() }

        val entries = remember(paragraphs) { normalizeEntries(paragraphs) }
        val anchorMap = remember(paragraphs) { buildAnchorMap(paragraphs) }
        val linkColor = Color(0xFF5B8DEF)
        // Full text map: paragraphIndex → complete original text (before any splitting)
        val fullTextMap = remember(entries) {
            entries.filter { it.isParagraphStart && it.imageSrc.isNullOrBlank() && it.text.isNotBlank() }
                .associate { it.paragraphIndex to it.text }
        }
        val fontSizePx = with(density) { fontSize.sp.toPx() }

        // Only completed translations participate in pagination (not streaming ones)
        val completedTranslationKeys = remember(translations) {
            translations.filterValues { it.isComplete && it.translatedText.isNotBlank() && !it.translatedText.startsWith("[") }.keys
        }

        // Stable completed keys — only update when pager is NOT scrolling
        // This prevents expensive re-pagination from running mid-animation
        var stableCompletedKeys by remember { mutableStateOf(emptySet<Int>()) }
        var stableTranslations by remember { mutableStateOf(emptyMap<Int, TranslationPair>()) }

        // Build bilingual entries when mode is on — interleave completed translations
        val paginationEntries = remember(entries, isBilingualMode, stableCompletedKeys) {
            if (isBilingualMode && stableCompletedKeys.isNotEmpty()) {
                buildBilingualEntries(entries, stableTranslations)
            } else {
                entries
            }
        }

        val pages = remember(paginationEntries, contentWidthPx, contentHeightPx, fontSize, lineHeight) {
            paginateEntries(
                entries = paginationEntries,
                contentWidthPx = contentWidthPx,
                contentHeightPx = contentHeightPx,
                fontSizePx = fontSizePx,
                lineHeight = lineHeight,
                maxImageHeightPx = maxImageHeightPx,
                paragraphGapPx = paragraphGapPx,
                density = density.density
            )
        }
        val paragraphToPageMap = remember(pages) { buildParagraphToPageMap(pages) }

        val pagerState = rememberPagerState(
            initialPage = 0,
            pageCount = { pages.size.coerceAtLeast(1) }
        )

        // Defer translation-triggered re-pagination until pager settles
        LaunchedEffect(completedTranslationKeys) {
            if (completedTranslationKeys == stableCompletedKeys) return@LaunchedEffect
            // Wait until pager finishes any active scroll/animation
            snapshotFlow { pagerState.isScrollInProgress }
                .first { !it }
            stableCompletedKeys = completedTranslationKeys
            stableTranslations = translations.filterValues {
                it.isComplete && it.translatedText.isNotBlank() && !it.translatedText.startsWith("[")
            }
        }

        LaunchedEffect(paragraphToPageMap) {
            onParagraphPageMapChanged?.invoke(paragraphToPageMap)
        }

        // Anchor state for position stability across re-pagination
        var readingAnchor by remember { mutableStateOf<ReadingAnchor?>(null) }
        var isRestoringAnchor by remember { mutableStateOf(false) }
        // When true, anchor restoration is active (set on translation toggle, cleared on user navigation)
        var forceAnchorRestore by remember { mutableStateOf(false) }
        var previousBilingualMode by remember { mutableStateOf(isBilingualMode) }

        if (previousBilingualMode != isBilingualMode) {
            forceAnchorRestore = true
            previousBilingualMode = isBilingualMode
        }

        // Helper to collect visible paragraphs on a given page + prefetch buffer (±1 page)
        fun collectVisibleParagraphs(pageIndex: Int): List<Pair<Int, String>> {
            val pagesToScan = listOfNotNull(
                pages.getOrNull(pageIndex - 1),
                pages.getOrNull(pageIndex),
                pages.getOrNull(pageIndex + 1)
            )
            return pagesToScan
                .flatMap { it.items }
                .asSequence()
                .filter { !it.isTranslation && it.isParagraphStart && it.imageSrc.isNullOrBlank() }
                .map { it.paragraphIndex }
                .distinct()
                .mapNotNull { idx -> fullTextMap[idx]?.let { idx to it } }
                .toList()
        }

        LaunchedEffect(pagerState, pages) {
            snapshotFlow { pagerState.currentPage }
                .distinctUntilChanged()
                .collect { raw ->
                    val safePage = raw.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
                    val total = pages.size.coerceAtLeast(1)
                    val progress = if (total <= 1) 0f else safePage.toFloat() / (total - 1).toFloat()
                    onProgressChanged(safePage, total, progress)

                    // Update reading anchor from current page's first non-translation entry
                    if (!isRestoringAnchor && !forceAnchorRestore) {
                        val firstOriginal = pages.getOrNull(safePage)?.items
                            ?.firstOrNull { !it.isTranslation && it.imageSrc.isNullOrBlank() && it.text.isNotBlank() }
                        if (firstOriginal != null) {
                            readingAnchor = ReadingAnchor(
                                paragraphIndex = firstOriginal.paragraphIndex,
                                textStartFraction = firstOriginal.textStartFraction
                            )
                        }
                    }
                    isRestoringAnchor = false

                    val visible = collectVisibleParagraphs(safePage)
                    if (visible.isNotEmpty()) onParagraphsVisible(visible)
                }
        }

        // Anchor restoration: when pages change due to translation insertion, restore position
        // Re-pagination is already deferred until pager settles, so no time guard needed
        LaunchedEffect(pages) {
            val anchor = readingAnchor ?: return@LaunchedEffect
            if (pages.isEmpty()) return@LaunchedEffect
            
            // Only restore if triggered by translation toggle
            if (!forceAnchorRestore) return@LaunchedEffect
            
            val targetPage = findPageForAnchor(pages, anchor)
            val currentPage = pagerState.currentPage.coerceIn(0, pages.lastIndex)
            if (targetPage != currentPage) {
                isRestoringAnchor = true
                pagerState.scrollToPage(targetPage)
            }
            forceAnchorRestore = false
        }

        // When bilingual mode is toggled on, translate the current page immediately
        // When bilingual mode is toggled on, translate the current page immediately
        LaunchedEffect(isBilingualMode) {
            if (isBilingualMode && pages.isNotEmpty()) {
                val safePage = pagerState.currentPage.coerceIn(0, pages.lastIndex)
                val visible = collectVisibleParagraphs(safePage)
                if (visible.isNotEmpty()) onParagraphsVisible(visible)
            }
        }

        LaunchedEffect(seekCommandId, seekPageIndex, seekParagraphIndex, seekProgress, pages, paragraphToPageMap) {
            if (seekCommandId == 0 || seekCommandId == lastHandledSeekCommandId || pages.isEmpty()) return@LaunchedEffect
            val target = when {
                seekPageIndex != null -> seekPageIndex
                seekParagraphIndex != null -> findNearestPage(seekParagraphIndex, paragraphToPageMap)
                seekProgress != null -> (seekProgress.coerceIn(0f, 1f) * pages.lastIndex.toFloat()).roundToInt()
                else -> pagerState.currentPage
            }.coerceIn(0, pages.lastIndex)
            pagerState.animateScrollToPage(target)
            lastHandledSeekCommandId = seekCommandId
        }

        HorizontalPager(
            state = pagerState,
            beyondBoundsPageCount = 1,
            userScrollEnabled = pages.size > 1 && selectionState == null,
            modifier = Modifier
                .fillMaxSize()
                .background(readerColors.background)
        ) { pageIndex ->
            val page = pages.getOrNull(pageIndex)
            if (page == null) return@HorizontalPager

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 24.dp, end = 24.dp, top = topPadding, bottom = bottomPadding)
            ) {
                // Tap overlay BEHIND content — handles page turns for non-link areas
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(pageIndex, pages.size, onSingleTap, isControlsVisible, selectionState) {
                            detectTapGestures { offset ->
                                if (selectionState != null) {
                                    selectionState = null
                                    onSingleTap?.invoke()
                                    return@detectTapGestures
                                }
                                if (isControlsVisible) {
                                    onSingleTap?.invoke()
                                    return@detectTapGestures
                                }
                                val width = size.width.toFloat().coerceAtLeast(1f)
                                val leftBoundary = width * 0.20f
                                val rightBoundary = width * 0.80f
                                when {
                                    offset.x < leftBoundary -> {
                                        if (pageIndex > 0) {
                                            scope.launch {
                                                pagerState.animateScrollToPage((pageIndex - 1).coerceAtLeast(0))
                                            }
                                        }
                                    }
                                    offset.x > rightBoundary -> {
                                        if (pageIndex < pages.lastIndex) {
                                            scope.launch {
                                                pagerState.animateScrollToPage((pageIndex + 1).coerceAtMost(pages.lastIndex))
                                            }
                                        }
                                    }
                                    else -> onSingleTap?.invoke()
                                }
                            }
                        }
                )

                // Content ON TOP — ClickableText for links intercepts taps; plain Text passes through
                Column(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight()
                ) {
                    page.items.forEachIndexed { idx, item ->
                        // Translation entries: render with smaller font, no gestures
                        if (item.isTranslation) {
                            val isFirstTranslationFragment = item.textStartFraction <= 0.001f
                            Text(
                                text = item.text,
                                style = TextStyle(
                                    fontSize = (fontSize - 1f).coerceAtLeast(12f).sp,
                                    lineHeight = (((fontSize - 1f).coerceAtLeast(12f)) * lineHeight).sp,
                                    color = secondaryTextColor
                                ),
                                modifier = if (isFirstTranslationFragment) Modifier.padding(top = 4.dp) else Modifier
                            )
                            if (idx != page.items.lastIndex) {
                                Spacer(modifier = Modifier.size(4.dp))
                            }
                            return@forEachIndexed
                        }

                        val isHighlighted = highlightParagraphIndex != null && item.paragraphIndex == highlightParagraphIndex && item.isParagraphStart
                        val highlightBg = if (isHighlighted) Modifier.background(linkHighlightColor, RoundedCornerShape(4.dp)) else Modifier

                        if (!item.imageSrc.isNullOrBlank()) {
                            val imageAspect = remember(item.imageSrc) { resolveImageAspectRatio(item.imageSrc) }
                            val imageHeight = with(density) {
                                val expectedPx = imageAspect
                                    ?.takeIf { it > 0f }
                                    ?.let { (contentWidthPx / it).coerceIn(140.dp.toPx(), maxImageHeightPx) }
                                    ?: maxImageHeightPx.coerceAtMost(280.dp.toPx())
                                expectedPx.toDp()
                            }
                            val imageModifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 140.dp, max = maxImageHeightDp)
                                .height(imageHeight)

                            val localBitmap = remember(item.imageSrc, contentWidthPx, maxImageHeightPx) {
                                decodeImageBitmap(item.imageSrc, contentWidthPx.roundToInt(), maxImageHeightPx.roundToInt())
                            }

                            if (localBitmap != null) {
                                Image(
                                    bitmap = localBitmap,
                                    contentDescription = item.text.ifBlank { null },
                                    contentScale = ContentScale.Fit,
                                    modifier = imageModifier
                                )
                            } else {
                                val model = resolveImageModel(item.imageSrc)
                                android.util.Log.d("NativeBookReader", "Loading image: src=${item.imageSrc}, model=$model")
                                if (model != null) {
                                    SubcomposeAsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(model)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = item.text.ifBlank { null },
                                        contentScale = ContentScale.Fit,
                                        modifier = imageModifier
                                    ) {
                                        when (val state = painter.state) {
                                            is AsyncImagePainter.State.Error -> {
                                                android.util.Log.e("NativeBookReader", "Coil load failed for $model", state.result.throwable)
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(40.dp)
                                                        .background(readerColors.onBackground.copy(alpha = 0.05f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "Image unavailable",
                                                        style = TextStyle(
                                                            fontSize = (fontSize - 2f).coerceAtLeast(10f).sp,
                                                            color = secondaryTextColor
                                                        )
                                                    )
                                                }
                                            }
                                            else -> SubcomposeAsyncImageContent()
                                        }
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(40.dp)
                                            .background(readerColors.onBackground.copy(alpha = 0.05f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Image unavailable (null src)",
                                            style = TextStyle(
                                                fontSize = (fontSize - 2f).coerceAtLeast(10f).sp,
                                                color = secondaryTextColor
                                            )
                                        )
                                    }
                                }
                            }
                            if (item.text.isNotBlank()) {
                                Text(
                                    text = item.text,
                                    style = TextStyle(
                                        fontSize = (fontSize - 1f).coerceAtLeast(12f).sp,
                                        lineHeight = (((fontSize - 1f).coerceAtLeast(12f)) * lineHeight).sp,
                                        color = secondaryTextColor
                                    ),
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                        } else {
                            val thisItemKey = pageIndex * 1000 + idx
                            val isThisSelected = selectionState?.itemKey == thisItemKey
                            val textStyle = if (item.isHeading) {
                                TextStyle(
                                    fontSize = (fontSize + 6f).sp,
                                    lineHeight = ((fontSize + 6f) * 1.24f).sp,
                                    color = textColor
                                )
                            } else {
                                TextStyle(
                                    fontSize = fontSize.sp,
                                    lineHeight = (fontSize * lineHeight).sp,
                                    color = textColor
                                )
                            }

                            // Build annotated string for link text, or use plain text
                            val displayText: CharSequence
                            val annotated: AnnotatedString?
                            if (item.links.isNotEmpty()) {
                                val built = buildAnnotatedString {
                                    var cursor = 0
                                    for (link in item.links.sortedBy { it.start }) {
                                        val s = link.start.coerceIn(0, item.text.length)
                                        val e = link.end.coerceIn(s, item.text.length)
                                        if (s > cursor) append(item.text.substring(cursor, s))
                                        pushStringAnnotation(tag = "link", annotation = link.href)
                                        withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                                            append(item.text.substring(s, e))
                                        }
                                        pop()
                                        cursor = e
                                    }
                                    if (cursor < item.text.length) append(item.text.substring(cursor))
                                }
                                annotated = built
                                displayText = built
                            } else {
                                annotated = null
                                displayText = item.text
                            }

                            var textLayout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
                            var itemCoordinates by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }

                            // Compute selection highlight via drawBehind
                            val selStart = if (isThisSelected) minOf(selectionState!!.startOffset, selectionState!!.endOffset).coerceIn(0, item.text.length) else 0
                            val selEnd = if (isThisSelected) maxOf(selectionState!!.startOffset, selectionState!!.endOffset).coerceIn(0, item.text.length) else 0

                            val selectionDrawModifier = Modifier.drawBehind {
                                if (isThisSelected && selStart < selEnd) {
                                    val layout = textLayout ?: return@drawBehind
                                    val path = layout.getPathForRange(selStart, selEnd)
                                    drawPath(path, color = selectionHighlightColor)
                                }
                            }

                            // Store layout coordinates for toolbar positioning
                            val positionModifier = Modifier.onGloballyPositioned { coordinates ->
                                itemCoordinates = coordinates
                            }

                            // Helper to compute rect and show toolbar
                            fun showSelectionToolbar() {
                                val state = selectionState ?: return
                                val layout = textLayout ?: return
                                val coords = itemCoordinates ?: return
                                val s = minOf(state.startOffset, state.endOffset).coerceIn(0, item.text.length)
                                val e = maxOf(state.startOffset, state.endOffset).coerceIn(0, item.text.length)
                                if (s >= e) return
                                val pos = coords.positionInRoot()
                                val startLine = layout.getLineForOffset(s)
                                val endLine = layout.getLineForOffset((e - 1).coerceAtLeast(s))
                                val lineTop = layout.getLineTop(startLine)
                                val lineBottom = layout.getLineBottom(endLine)
                                val left = layout.getHorizontalPosition(s, true)
                                val right = layout.getHorizontalPosition(e, true)
                                val selectedText = item.text.substring(s, e)
                                val rect = SelectionRect(
                                    left = (pos.x + if (startLine == endLine) left else 0f) / density.density,
                                    top = (pos.y + lineTop) / density.density,
                                    right = (pos.x + if (startLine == endLine) right else coords.size.width.toFloat()) / density.density,
                                    bottom = (pos.y + lineBottom) / density.density
                                )
                                onParagraphLongPress?.invoke(item.paragraphIndex, selectedText, rect)
                            }

                            // Drag-to-select gesture
                            val dragSelectModifier = Modifier.pointerInput(thisItemKey, onParagraphLongPress) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { offset ->
                                        val layout = textLayout ?: return@detectDragGesturesAfterLongPress
                                        val charOffset = layout.getOffsetForPosition(offset)
                                        val (wStart, wEnd) = findWordBoundary(item.text, charOffset)
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        selectionState = NativeTextSelection(
                                            itemKey = thisItemKey,
                                            paragraphIndex = item.paragraphIndex,
                                            startOffset = wStart,
                                            endOffset = wEnd,
                                            anchorWordStart = wStart,
                                            anchorWordEnd = wEnd,
                                            isDragging = true
                                        )
                                    },
                                    onDrag = { change, _ ->
                                        val layout = textLayout ?: return@detectDragGesturesAfterLongPress
                                        val charOffset = layout.getOffsetForPosition(change.position)
                                        val current = selectionState ?: return@detectDragGesturesAfterLongPress
                                        selectionState = if (charOffset < current.anchorWordStart) {
                                            current.copy(startOffset = charOffset, endOffset = current.anchorWordEnd)
                                        } else if (charOffset > current.anchorWordEnd) {
                                            current.copy(startOffset = current.anchorWordStart, endOffset = charOffset)
                                        } else {
                                            current.copy(startOffset = current.anchorWordStart, endOffset = current.anchorWordEnd)
                                        }
                                        change.consume()
                                    },
                                    onDragEnd = {
                                        selectionState = selectionState?.copy(isDragging = false)
                                        showSelectionToolbar()
                                    },
                                    onDragCancel = {
                                        // Also finalize selection (fires when user lifts without much drag)
                                        selectionState = selectionState?.copy(isDragging = false)
                                        showSelectionToolbar()
                                    }
                                )
                            }

                            // Tap gesture for links and page turns
                            val tapModifier = Modifier.pointerInput(annotated, anchorMap, pageIndex, pages.size, onSingleTap, isControlsVisible, selectionState) {
                                detectTapGestures { tapOffset ->
                                    // Clear active selection on any tap
                                    if (selectionState != null) {
                                        selectionState = null
                                        onSingleTap?.invoke()
                                        return@detectTapGestures
                                    }
                                    if (annotated != null) {
                                        val layout = textLayout ?: return@detectTapGestures
                                        val charOffset = layout.getOffsetForPosition(tapOffset)
                                        val ann = annotated.getStringAnnotations("link", charOffset, charOffset).firstOrNull()
                                            ?: annotated.getStringAnnotations("link", (charOffset - 1).coerceAtLeast(0), (charOffset + 1).coerceAtMost(annotated.length)).firstOrNull()
                                        if (ann != null) {
                                            val targetId = ann.item.removePrefix("#")
                                            val targetParagraphIndex = anchorMap[targetId]
                                            if (targetParagraphIndex != null) {
                                                onLinkClicked?.invoke(targetParagraphIndex)
                                            }
                                            return@detectTapGestures
                                        }
                                    }
                                    // No link — handle page turn
                                    if (isControlsVisible) {
                                        onSingleTap?.invoke()
                                        return@detectTapGestures
                                    }
                                    val width = size.width.toFloat().coerceAtLeast(1f)
                                    when {
                                        tapOffset.x < width * 0.20f -> {
                                            if (pageIndex > 0) scope.launch { pagerState.animateScrollToPage(pageIndex - 1) }
                                        }
                                        tapOffset.x > width * 0.80f -> {
                                            if (pageIndex < pages.lastIndex) scope.launch { pagerState.animateScrollToPage(pageIndex + 1) }
                                        }
                                        else -> onSingleTap?.invoke()
                                    }
                                }
                            }

                            val commonModifier = highlightBg
                                    .then(selectionDrawModifier)
                                    .then(positionModifier)
                                    .then(dragSelectModifier)
                                    .then(tapModifier)

                            if (annotated != null) {
                                Text(
                                    text = annotated,
                                    style = textStyle,
                                    maxLines = Int.MAX_VALUE,
                                    overflow = TextOverflow.Clip,
                                    onTextLayout = { textLayout = it },
                                    modifier = commonModifier
                                )
                            } else {
                                Text(
                                    text = item.text,
                                    style = textStyle,
                                    maxLines = Int.MAX_VALUE,
                                    overflow = TextOverflow.Clip,
                                    onTextLayout = { textLayout = it },
                                    modifier = commonModifier
                                )
                            }
                        }

                        if (idx != page.items.lastIndex) {
                            Spacer(modifier = Modifier.size(4.dp))
                        }
                    }
                }
            }
        }
    }
}

private data class LinkSpan(val start: Int, val end: Int, val href: String)

private data class NormalizedEntry(
    val paragraphIndex: Int,
    val text: String,
    val isHeading: Boolean,
    val isParagraphStart: Boolean,
    val imageSrc: String? = null,
    val links: List<LinkSpan> = emptyList(),
    val textStartFraction: Float = 0f,
    val textEndFraction: Float = 1f,
    val isTranslation: Boolean = false
)

/** Semantic reading position: paragraph + fraction within that paragraph */
private data class ReadingAnchor(
    val paragraphIndex: Int,
    val textStartFraction: Float
)

private data class ReaderPage(
    val items: List<NormalizedEntry>
)

private fun normalizeEntries(paragraphs: List<ReaderParagraph>): List<NormalizedEntry> {
    return paragraphs.mapNotNull { paragraph ->
        if (!paragraph.imageSrc.isNullOrBlank()) {
            val caption = paragraph.text.trim().ifBlank {
                if (paragraph.html.isNotBlank()) Jsoup.parse(paragraph.html).text().trim() else ""
            }
            val normalizedCaption = caption.takeUnless {
                it.equals("image", ignoreCase = true) ||
                    it.equals("img", ignoreCase = true) ||
                    it.equals("figure", ignoreCase = true)
            }.orEmpty()
            
            android.util.Log.d("NativeBookReader", "Normalized Image: index=${paragraph.index}, src=${paragraph.imageSrc}")
            
            return@mapNotNull NormalizedEntry(
                paragraphIndex = paragraph.index,
                text = normalizedCaption,
                isHeading = false,
                isParagraphStart = true,
                imageSrc = paragraph.imageSrc
            )
        }

        val parsed = extractTextAndLinks(paragraph.html, paragraph.text)
        val text = parsed.first
        val links = parsed.second

        if (text.isBlank()) return@mapNotNull null
        if (!paragraph.isHeading && isGenericImageLabel(text)) return@mapNotNull null

        return@mapNotNull NormalizedEntry(
            paragraphIndex = paragraph.index,
            text = text,
            isHeading = paragraph.isHeading,
            isParagraphStart = true,
            imageSrc = null,
            links = links
        )
    }
}

private fun isGenericImageLabel(text: String): Boolean {
    val normalized = text.trim().lowercase()
    if (normalized.isBlank()) return true
    return normalized == "image" ||
        normalized == "img" ||
        normalized == "figure" ||
        normalized.matches(Regex("""figure\s*\d+[\.:]?"""))
}

private fun extractTextAndLinks(html: String, fallbackText: String): Pair<String, List<LinkSpan>> {
    if (html.isBlank()) {
        return fallbackText.replace(Regex("\\s+"), " ").trim() to emptyList()
    }
    val doc = Jsoup.parse(html)
    val sb = StringBuilder()
    val links = mutableListOf<LinkSpan>()
    // Walk the DOM tree to build plain text and track link positions precisely
    walkNode(doc.body(), sb, links, null)
    val plainText = sb.toString().replace(Regex("\\s+"), " ").trim()
    // Recompute link offsets after whitespace normalization
    return plainText to remapLinksAfterNormalization(sb.toString(), plainText, links)
}

private fun walkNode(
    node: org.jsoup.nodes.Node,
    sb: StringBuilder,
    links: MutableList<LinkSpan>,
    currentHref: String?
) {
    when (node) {
        is org.jsoup.nodes.TextNode -> {
            val text = node.wholeText
            if (text.isNotEmpty()) {
                sb.append(text)
            }
        }
        is org.jsoup.nodes.Element -> {
            val isLink = node.tagName() == "a" && node.hasAttr("href")
            val href = if (isLink) {
                val h = node.attr("href").trim()
                if (h.isNotBlank() && h.startsWith("#")) h else null
            } else {
                currentHref
            }
            val linkStart = if (isLink && href != null) sb.length else -1
            for (child in node.childNodes()) {
                walkNode(child, sb, links, href ?: currentHref)
            }
            if (isLink && href != null && linkStart >= 0 && sb.length > linkStart) {
                links += LinkSpan(start = linkStart, end = sb.length, href = href)
            }
        }
    }
}

private fun remapLinksAfterNormalization(
    raw: String,
    normalized: String,
    links: List<LinkSpan>
): List<LinkSpan> {
    if (links.isEmpty()) return emptyList()
    // Build mapping: raw char index → normalized char index
    val rawToNorm = IntArray(raw.length + 1) { -1 }
    var ni = 0
    var ri = 0
    // Skip leading whitespace in normalized (it's trimmed)
    val leadingTrimmed = raw.length - raw.trimStart().length
    ri = leadingTrimmed
    ni = 0
    while (ri < raw.length && ni < normalized.length) {
        if (raw[ri] == normalized[ni]) {
            rawToNorm[ri] = ni
            ri++
            ni++
        } else if (raw[ri].isWhitespace()) {
            // This whitespace was collapsed or trimmed
            rawToNorm[ri] = ni
            ri++
        } else {
            // Should not happen in normal cases
            ri++
        }
    }
    // Map remaining chars
    while (ri <= raw.length) {
        rawToNorm[ri] = ni.coerceAtMost(normalized.length)
        ri++
    }
    return links.mapNotNull { link ->
        val newStart = rawToNorm.getOrNull(link.start) ?: return@mapNotNull null
        val newEnd = rawToNorm.getOrNull(link.end) ?: rawToNorm.getOrNull(link.end.coerceAtMost(raw.length)) ?: return@mapNotNull null
        if (newStart >= newEnd || newStart >= normalized.length) return@mapNotNull null
        LinkSpan(start = newStart, end = newEnd.coerceAtMost(normalized.length), href = link.href)
    }
}

private fun buildAnchorMap(paragraphs: List<ReaderParagraph>): Map<String, Int> {
    val map = mutableMapOf<String, Int>()
    for (p in paragraphs) {
        if (p.html.isBlank()) continue
        val doc = Jsoup.parse(p.html)
        doc.select("[id]").forEach { el ->
            val id = el.id().trim()
            if (id.isNotBlank()) map.putIfAbsent(id, p.index)
        }
        doc.select("a[name]").forEach { el ->
            val name = el.attr("name").trim()
            if (name.isNotBlank()) map.putIfAbsent(name, p.index)
        }
    }
    return map
}

private fun paginateEntries(
    entries: List<NormalizedEntry>,
    contentWidthPx: Float,
    contentHeightPx: Float,
    fontSizePx: Float,
    lineHeight: Float,
    maxImageHeightPx: Float,
    paragraphGapPx: Float,
    density: Float
): List<ReaderPage> {
    if (entries.isEmpty()) return emptyList()

    val pages = mutableListOf<List<NormalizedEntry>>()
    var current = mutableListOf<NormalizedEntry>()
    var currentHeight = 0f

    fun flush() {
        if (current.isNotEmpty()) {
            pages += current.toList()
            current = mutableListOf()
            currentHeight = 0f
        }
    }

    entries.forEach { entry ->
        var pending = entry
        while (true) {
            val pendingHeight = measureEntryHeight(
                entry = pending,
                widthPx = contentWidthPx,
                fontSizePx = fontSizePx,
                lineHeight = lineHeight,
                maxImageHeightPx = maxImageHeightPx,
                paragraphGapPx = paragraphGapPx,
                density = density
            )

            // If it's an image and doesn't fit, flush current page and put it on next
            if (!pending.imageSrc.isNullOrBlank()) {
                if (current.isNotEmpty() && currentHeight + pendingHeight + paragraphGapPx > contentHeightPx) {
                    flush()
                }
                current += pending
                currentHeight += pendingHeight + paragraphGapPx
                break
            }

            // For text, check if it fits or needs splitting
            if (currentHeight + pendingHeight <= contentHeightPx) {
                current += pending
                currentHeight += pendingHeight + paragraphGapPx
                break
            } else {
                // Try to split the text to fill the remaining space exactly
                val remainingHeight = contentHeightPx - currentHeight
                val split = splitEntryByHeight(
                    entry = pending,
                    widthPx = contentWidthPx,
                    availableHeightPx = remainingHeight - (paragraphGapPx * 0.5f),
                    fontSizePx = fontSizePx,
                    lineHeight = lineHeight,
                    density = density
                )

                if (split != null) {
                    current += split.first
                    flush()
                    pending = split.second
                    // Continue loop with the tail
                    continue
                } else {
                    // Could not split (e.g., first line doesn't even fit)
                    if (current.isNotEmpty()) {
                        flush()
                        // Continue loop with the same pending entry on a fresh page
                        continue
                    } else {
                        // On a fresh page and still doesn't fit?
                        // Force it in or split it even if it's the first line
                        current += pending
                        currentHeight += pendingHeight
                        break
                    }
                }
            }
        }
    }

    flush()
    return pages.map { ReaderPage(items = it) }
}

/** Compute text size in px for a given entry, matching what Compose renders */
private fun translationAwareTextSize(entry: NormalizedEntry, fontSizePx: Float, density: Float): Float {
    return when {
        entry.isTranslation -> {
            // Must match Compose: (fontSize - 1f).coerceAtLeast(12f).sp
            // fontSizePx = fontSize.sp.toPx() = fontSize * density
            // translationSp = (fontSize - 1f).coerceAtLeast(12f)
            // translationPx = translationSp * density
            val fontSizeSp = fontSizePx / density
            ((fontSizeSp - 1f).coerceAtLeast(12f)) * density
        }
        entry.isHeading -> fontSizePx * 1.35f
        else -> fontSizePx
    }
}

private fun splitEntryByHeight(
    entry: NormalizedEntry,
    widthPx: Float,
    availableHeightPx: Float,
    fontSizePx: Float,
    lineHeight: Float,
    density: Float = 1f
): Pair<NormalizedEntry, NormalizedEntry>? {
    if (!entry.imageSrc.isNullOrBlank()) return null
    if (entry.text.length < 2) return null

    // Account for top padding of translation entries
    val effectiveAvailable = if (entry.isTranslation) availableHeightPx - 4f * density else availableHeightPx
    if (effectiveAvailable <= 0f) return null

    val textSizePx = translationAwareTextSize(entry, fontSizePx, density)
    val targetLineHeightPx = when {
        entry.isTranslation -> textSizePx * lineHeight
        entry.isHeading -> textSizePx * 1.24f
        else -> fontSizePx * lineHeight
    }
    val layout = buildStaticLayout(
        text = entry.text,
        textSizePx = textSizePx,
        targetLineHeightPx = targetLineHeightPx,
        widthPx = widthPx.roundToInt().coerceAtLeast(120)
    )
    if (layout.lineCount <= 1) return null

    var lastLine = -1
    for (line in 0 until layout.lineCount) {
        if (layout.getLineBottom(line).toFloat() <= effectiveAvailable) {
            lastLine = line
        } else {
            break
        }
    }
    if (lastLine < 0 || lastLine >= layout.lineCount - 1) return null

    val splitEnd = layout.getLineEnd(lastLine).coerceIn(1, entry.text.length - 1)
    val headText = entry.text.substring(0, splitEnd).trim()
    val tailText = entry.text.substring(splitEnd).trim()
    if (headText.isBlank() || tailText.isBlank()) return null

    val headLinks = entry.links.mapNotNull { link ->
        if (link.start >= splitEnd) return@mapNotNull null
        LinkSpan(link.start, link.end.coerceAtMost(splitEnd), link.href)
    }.filter { it.start < it.end }
    // Compute offset for tail: characters before splitEnd may have been trimmed
    val tailOffset = splitEnd
    val tailLinks = entry.links.mapNotNull { link ->
        if (link.end <= tailOffset) return@mapNotNull null
        val s = (link.start - tailOffset).coerceAtLeast(0)
        val e = link.end - tailOffset
        if (s >= e || s >= tailText.length) return@mapNotNull null
        LinkSpan(s, e.coerceAtMost(tailText.length), link.href)
    }
    // Compute text offset fractions for viewport-based translation slicing
    val fragmentLength = entry.text.length.coerceAtLeast(1)
    val splitRatio = splitEnd.toFloat() / fragmentLength.toFloat()
    val fragmentRange = entry.textEndFraction - entry.textStartFraction
    val splitFraction = entry.textStartFraction + splitRatio * fragmentRange

    val head = entry.copy(text = headText, links = headLinks, textEndFraction = splitFraction)
    val tail = entry.copy(text = tailText, isParagraphStart = false, links = tailLinks, textStartFraction = splitFraction)
    return head to tail
}

private fun measureEntryHeight(
    entry: NormalizedEntry,
    widthPx: Float,
    fontSizePx: Float,
    lineHeight: Float,
    maxImageHeightPx: Float,
    paragraphGapPx: Float,
    density: Float
): Float {
    if (!entry.imageSrc.isNullOrBlank()) {
        val aspect = resolveImageAspectRatio(entry.imageSrc)
        val minHeightPx = 140f * density
        val fallbackHeightPx = 280f * density
        val imageHeightPx = if (aspect != null && aspect > 0f) {
            (widthPx / aspect).coerceIn(minHeightPx, maxImageHeightPx)
        } else {
            maxImageHeightPx.coerceAtMost(fallbackHeightPx)
        }
        val captionHeight = if (entry.text.isBlank()) 0f else fontSizePx * lineHeight * 1.2f
        return imageHeightPx + captionHeight
    }

    val textSizePx = translationAwareTextSize(entry, fontSizePx, density)
    val targetLineHeightPx = when {
        entry.isTranslation -> textSizePx * lineHeight
        entry.isHeading -> textSizePx * 1.24f
        else -> fontSizePx * lineHeight
    }
    val layout = buildStaticLayout(
        text = entry.text,
        textSizePx = textSizePx,
        targetLineHeightPx = targetLineHeightPx,
        widthPx = widthPx.roundToInt().coerceAtLeast(120)
    )
    // Add top padding only for the first fragment of translation entries (4.dp equivalent)
    val extraPadding = if (entry.isTranslation && entry.textStartFraction <= 0.001f) 4f * density else 0f
    return layout.height.toFloat() + extraPadding
}

private fun buildStaticLayout(
    text: CharSequence,
    textSizePx: Float,
    targetLineHeightPx: Float,
    widthPx: Int
): StaticLayout {
    val paint = TextPaint().apply {
        textSize = textSizePx
        isAntiAlias = true
    }
    val fm = paint.fontMetrics
    val naturalLineHeight = fm.descent - fm.ascent
    val extraSpacing = (targetLineHeightPx - naturalLineHeight).coerceAtLeast(0f)
    return StaticLayout.Builder.obtain(text, 0, text.length, paint, widthPx)
        .setIncludePad(false)
        .setLineSpacing(extraSpacing, 1f)
        .build()
}

private fun findNearestPage(paragraphIndex: Int, paragraphToPageMap: Map<Int, Int>): Int {
    paragraphToPageMap[paragraphIndex]?.let { return it }
    // If exact paragraph isn't in the page map (filtered out), find the nearest one after it
    for (offset in 1..50) {
        paragraphToPageMap[paragraphIndex + offset]?.let {
            android.util.Log.d("NativeBookReader", "findNearestPage: exact $paragraphIndex not found, using ${paragraphIndex + offset} → page $it")
            return it
        }
        if (paragraphIndex - offset >= 0) {
            paragraphToPageMap[paragraphIndex - offset]?.let {
                android.util.Log.d("NativeBookReader", "findNearestPage: exact $paragraphIndex not found, using ${paragraphIndex - offset} → page $it")
                return it
            }
        }
    }
    return 0
}

private fun buildParagraphToPageMap(pages: List<ReaderPage>): Map<Int, Int> {
    val map = mutableMapOf<Int, Int>()
    pages.forEachIndexed { pageIndex, page ->
        page.items.forEach { item ->
            if (!item.isTranslation) {
                map.putIfAbsent(item.paragraphIndex, pageIndex)
            }
        }
    }
    return map
}

/**
 * Build bilingual entries by interleaving completed translation entries after each original
 * paragraph's last fragment. Only completed translations are included to avoid re-pagination
 * during streaming.
 */
private fun buildBilingualEntries(
    entries: List<NormalizedEntry>,
    translations: Map<Int, TranslationPair>
): List<NormalizedEntry> {
    if (translations.isEmpty()) return entries
    val result = mutableListOf<NormalizedEntry>()
    for (entry in entries) {
        result += entry
        // Insert translation after the last fragment of a paragraph
        // A fragment is the "last" if the next entry has a different paragraphIndex or is a new paragraph start, or this is the last entry
        val isLastFragment = entry.imageSrc.isNullOrBlank() && entry.text.isNotBlank() && !entry.isTranslation
        if (isLastFragment) {
            val nextIdx = entries.indexOf(entry) + 1
            val isActuallyLast = nextIdx >= entries.size ||
                entries[nextIdx].paragraphIndex != entry.paragraphIndex ||
                entries[nextIdx].isParagraphStart
            if (isActuallyLast) {
                val pair = translations[entry.paragraphIndex]
                if (pair != null && pair.isComplete && pair.translatedText.isNotBlank() &&
                    !pair.translatedText.startsWith("[")) {
                    result += NormalizedEntry(
                        paragraphIndex = entry.paragraphIndex,
                        text = pair.translatedText,
                        isHeading = false,
                        isParagraphStart = false,
                        isTranslation = true
                    )
                }
            }
        }
    }
    return result
}

/**
 * Find the page index containing the given anchor position after re-pagination.
 * Returns the page where the paragraph with matching paragraphIndex and textStartFraction is found.
 */
private fun findPageForAnchor(pages: List<ReaderPage>, anchor: ReadingAnchor): Int {
    for ((pageIdx, page) in pages.withIndex()) {
        for (item in page.items) {
            if (item.isTranslation) continue
            if (item.paragraphIndex == anchor.paragraphIndex &&
                item.textStartFraction <= anchor.textStartFraction + 0.01f &&
                item.textEndFraction >= anchor.textStartFraction - 0.01f) {
                return pageIdx
            }
        }
    }
    // Fallback: find nearest paragraph
    for ((pageIdx, page) in pages.withIndex()) {
        if (page.items.any { !it.isTranslation && it.paragraphIndex == anchor.paragraphIndex }) {
            return pageIdx
        }
    }
    return 0
}

private fun resolveImageModel(imageSrc: String?): Any? {
    val src = imageSrc?.trim().orEmpty()
    if (src.isBlank()) return null
    if (!src.startsWith("data:", ignoreCase = true)) {
        return if (src.startsWith("/")) File(src) else src
    }
    return decodeDataUri(src) ?: src
}

private fun resolveImageAspectRatio(imageSrc: String?): Float? {
    val model = resolveImageModel(imageSrc) ?: return null
    val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    return when (model) {
        is File -> {
            runCatching {
                android.graphics.BitmapFactory.decodeFile(model.absolutePath, options)
                val w = options.outWidth.toFloat()
                val h = options.outHeight.toFloat()
                if (w > 0f && h > 0f) w / h else null
            }.getOrNull()
        }
        is ByteArray -> {
            runCatching {
                android.graphics.BitmapFactory.decodeByteArray(model, 0, model.size, options)
                val w = options.outWidth.toFloat()
                val h = options.outHeight.toFloat()
                if (w > 0f && h > 0f) w / h else null
            }.getOrNull()
        }
        else -> null
    }
}

private fun decodeDataUri(dataUri: String): ByteArray? {
    val commaIndex = dataUri.indexOf(',')
    if (commaIndex <= 0 || commaIndex >= dataUri.lastIndex) return null
    val header = dataUri.substring(0, commaIndex)
    val payload = dataUri.substring(commaIndex + 1)
    return runCatching {
        if (header.contains(";base64", ignoreCase = true)) {
            Base64.getDecoder().decode(payload)
        } else {
            URLDecoder.decode(payload, Charsets.UTF_8.name()).toByteArray(Charsets.UTF_8)
        }
    }.getOrNull()
}

private fun decodeImageBitmap(imageSrc: String?, reqWidth: Int, reqHeight: Int): androidx.compose.ui.graphics.ImageBitmap? {
    val src = imageSrc?.trim().orEmpty()
    if (src.isBlank()) return null
    return runCatching {
        val sourceBytes = if (src.startsWith("data:", ignoreCase = true)) decodeDataUri(src) else null
        val options = android.graphics.BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        if (sourceBytes != null) {
            android.graphics.BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size, options)
        } else if (src.startsWith("/")) {
            android.graphics.BitmapFactory.decodeFile(src, options)
        } else {
            return null
        }
        val outW = options.outWidth.coerceAtLeast(1)
        val outH = options.outHeight.coerceAtLeast(1)
        val sample = calculateInSampleSize(
            width = outW,
            height = outH,
            reqWidth = reqWidth.coerceAtLeast(1),
            reqHeight = reqHeight.coerceAtLeast(1)
        )
        val decodeOptions = android.graphics.BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
        }
        val bitmap = if (sourceBytes != null) {
            android.graphics.BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size, decodeOptions)
        } else {
            android.graphics.BitmapFactory.decodeFile(src, decodeOptions)
        } ?: return null
        bitmap.asImageBitmap()
    }.getOrNull()
}

private fun calculateInSampleSize(
    width: Int,
    height: Int,
    reqWidth: Int,
    reqHeight: Int
): Int {
    var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize.coerceAtLeast(1)
}

private data class NativeTextSelection(
    val itemKey: Int,
    val paragraphIndex: Int,
    val startOffset: Int,
    val endOffset: Int,
    val anchorWordStart: Int,
    val anchorWordEnd: Int,
    val isDragging: Boolean = false
)

private fun findWordBoundary(text: String, offset: Int): Pair<Int, Int> {
    if (text.isEmpty()) return 0 to 0
    val safeOffset = offset.coerceIn(0, text.length - 1)
    // For CJK characters, select single character
    val ch = text[safeOffset]
    if (ch.code in 0x4E00..0x9FFF || ch.code in 0x3400..0x4DBF || ch.code in 0x3000..0x303F) {
        return safeOffset to (safeOffset + 1)
    }
    // For other text, find word boundaries
    var start = safeOffset
    while (start > 0 && !text[start - 1].isWhitespace() && text[start - 1] !in ".,;:!?()[]{}\"'") start--
    var end = safeOffset
    while (end < text.length && !text[end].isWhitespace() && text[end] !in ".,;:!?()[]{}\"'") end++
    if (start == end && end < text.length) end++
    return start to end
}

