package com.readassistant.feature.reader.presentation.renderer

import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
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
    val highlightColor = Color(0xFF5B8DEF).copy(alpha = 0.25f)
    val density = LocalDensity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var lastHandledSeekCommandId by rememberSaveable { mutableIntStateOf(0) }

    BoxWithConstraints(modifier = modifier) {
        val statusBarTopPadding = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
        val navBarBottomPadding = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
        val topPadding = statusBarTopPadding + 16.dp
        val bottomPadding = navBarBottomPadding + 4.dp

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
        val pages = remember(entries, contentWidthPx, contentHeightPx, fontSize, lineHeight, isBilingualMode) {
            paginateEntries(
                entries = entries,
                contentWidthPx = contentWidthPx,
                contentHeightPx = if (isBilingualMode) contentHeightPx * 0.92f else contentHeightPx,
                fontSizePx = with(density) { fontSize.sp.toPx() },
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

        LaunchedEffect(paragraphToPageMap) {
            onParagraphPageMapChanged?.invoke(paragraphToPageMap)
        }

        LaunchedEffect(pagerState, pages) {
            snapshotFlow { pagerState.currentPage }
                .distinctUntilChanged()
                .collect { raw ->
                    val safePage = raw.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
                    val total = pages.size.coerceAtLeast(1)
                    val progress = if (total <= 1) 0f else safePage.toFloat() / (total - 1).toFloat()
                    onProgressChanged(safePage, total, progress)

                    val visible = pages.getOrNull(safePage)
                        ?.items
                        ?.asSequence()
                        ?.filter { it.isParagraphStart && it.imageSrc.isNullOrBlank() }
                        ?.map { it.paragraphIndex to it.text }
                        ?.filter { it.second.isNotBlank() }
                        ?.distinctBy { it.first }
                        ?.toList()
                        .orEmpty()
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
            userScrollEnabled = pages.size > 1,
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
                        .pointerInput(pageIndex, pages.size, onSingleTap, isControlsVisible) {
                            detectTapGestures { offset ->
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                ) {
                    page.items.forEachIndexed { idx, item ->
                        val isHighlighted = highlightParagraphIndex != null && item.paragraphIndex == highlightParagraphIndex && item.isParagraphStart
                        val highlightBg = if (isHighlighted) Modifier.background(highlightColor, RoundedCornerShape(4.dp)) else Modifier

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
                            if (item.links.isNotEmpty()) {
                                val annotated = buildAnnotatedString {
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
                                // Use Text + pointerInput to handle BOTH link clicks and page turns
                                var textLayout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
                                Text(
                                    text = annotated,
                                    style = textStyle,
                                    maxLines = Int.MAX_VALUE,
                                    overflow = TextOverflow.Clip,
                                    onTextLayout = { textLayout = it },
                                    modifier = highlightBg.then(Modifier.pointerInput(annotated, anchorMap, pageIndex, pages.size, onSingleTap, isControlsVisible) {
                                        detectTapGestures { tapOffset ->
                                            val layout = textLayout ?: return@detectTapGestures
                                            val charOffset = layout.getOffsetForPosition(tapOffset)
                                            // Expand search radius for small targets like * or †
                                            val ann = annotated.getStringAnnotations("link", charOffset, charOffset).firstOrNull()
                                                ?: annotated.getStringAnnotations("link", (charOffset - 1).coerceAtLeast(0), (charOffset + 1).coerceAtMost(annotated.length)).firstOrNull()
                                            if (ann != null) {
                                                val targetId = ann.item.removePrefix("#")
                                                val targetParagraphIndex = anchorMap[targetId]
                                                android.util.Log.d("NativeBookReader", "Link clicked: href=${ann.item}, targetId=$targetId, paragraphIndex=$targetParagraphIndex, pageMapHit=${paragraphToPageMap[targetParagraphIndex]}")
                                                if (targetParagraphIndex != null) {
                                                    onLinkClicked?.invoke(targetParagraphIndex)
                                                } else {
                                                    android.util.Log.w("NativeBookReader", "Anchor not found: $targetId. Available anchors(first 20): ${anchorMap.keys.take(20)}")
                                                }
                                            } else {
                                                // No link at tap position — handle page turn
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
                                    })
                                )
                            } else {
                                Text(
                                    text = item.text,
                                    style = textStyle,
                                    maxLines = Int.MAX_VALUE,
                                    overflow = TextOverflow.Clip,
                                    modifier = highlightBg
                                )
                            }
                            if (isBilingualMode && item.isParagraphStart) {
                                val translated = translations[item.paragraphIndex]?.translatedText.orEmpty()
                                if (translated.isNotBlank() && !isSameReadingText(translated, item.text)) {
                                    Text(
                                        text = translated,
                                        style = TextStyle(
                                            fontSize = (fontSize - 1f).coerceAtLeast(12f).sp,
                                            lineHeight = (((fontSize - 1f).coerceAtLeast(12f)) * lineHeight).sp,
                                            color = secondaryTextColor
                                        ),
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
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
    val links: List<LinkSpan> = emptyList()
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
                    lineHeight = lineHeight
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

private fun splitEntryByHeight(
    entry: NormalizedEntry,
    widthPx: Float,
    availableHeightPx: Float,
    fontSizePx: Float,
    lineHeight: Float
): Pair<NormalizedEntry, NormalizedEntry>? {
    if (!entry.imageSrc.isNullOrBlank()) return null
    if (entry.text.length < 2) return null

    val textSizePx = if (entry.isHeading) fontSizePx * 1.35f else fontSizePx
    val targetLineHeightPx = if (entry.isHeading) textSizePx * 1.24f else fontSizePx * lineHeight
    val layout = buildStaticLayout(
        text = entry.text,
        textSizePx = textSizePx,
        targetLineHeightPx = targetLineHeightPx,
        widthPx = widthPx.roundToInt().coerceAtLeast(120)
    )
    if (layout.lineCount <= 1) return null

    var lastLine = -1
    for (line in 0 until layout.lineCount) {
        if (layout.getLineBottom(line).toFloat() <= availableHeightPx) {
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
    val head = entry.copy(text = headText, links = headLinks)
    val tail = entry.copy(text = tailText, isParagraphStart = false, links = tailLinks)
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

    val textSizePx = if (entry.isHeading) fontSizePx * 1.35f else fontSizePx
    val targetLineHeightPx = if (entry.isHeading) textSizePx * 1.24f else fontSizePx * lineHeight
    val layout = buildStaticLayout(
        text = entry.text,
        textSizePx = textSizePx,
        targetLineHeightPx = targetLineHeightPx,
        widthPx = widthPx.roundToInt().coerceAtLeast(120)
    )
    return layout.height.toFloat()
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
            map.putIfAbsent(item.paragraphIndex, pageIndex)
        }
    }
    return map
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

private fun isSameReadingText(a: String, b: String): Boolean {
    fun normalize(text: String): String {
        return text
            .lowercase()
            .replace(Regex("[\\s\\p{Punct}，。！？；：“”‘’、（）《》【】]+"), "")
            .trim()
    }
    val na = normalize(a)
    val nb = normalize(b)
    return na.isNotBlank() && na == nb
}
