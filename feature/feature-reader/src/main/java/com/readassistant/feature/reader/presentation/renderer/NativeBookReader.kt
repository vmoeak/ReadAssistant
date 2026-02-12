package com.readassistant.feature.reader.presentation.renderer

import android.content.Intent
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.view.View
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import android.text.StaticLayout
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.readassistant.core.ui.theme.ReadingThemeType
import com.readassistant.feature.reader.presentation.reader.ReaderParagraph
import com.readassistant.feature.translation.domain.TranslationPair
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import org.jsoup.nodes.TextNode
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
    seekProgress: Float? = null,
    onProgressChanged: (currentIndex: Int, totalItems: Int, progress: Float) -> Unit,
    onParagraphPageMapChanged: ((Map<Int, Int>) -> Unit)? = null,
    onParagraphsVisible: (List<Pair<Int, String>>) -> Unit,
    onSingleTap: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val textColor = when (themeType) {
        ReadingThemeType.LIGHT -> MaterialTheme.colorScheme.onSurface
        ReadingThemeType.SEPIA -> MaterialTheme.colorScheme.onSurface
        ReadingThemeType.DARK -> MaterialTheme.colorScheme.onSurface
    }
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var lastHandledSeekCommandId by rememberSaveable { mutableIntStateOf(0) }

    BoxWithConstraints(modifier = modifier) {
        val viewportWidthPx = with(density) { maxWidth.toPx() }
        val pageChromeReservedPx = with(density) { (56.dp + 10.dp + 10.dp + 28.dp).toPx() }
        val viewportHeightPx = (with(density) { maxHeight.toPx() } - pageChromeReservedPx)
            .coerceAtLeast(with(density) { (fontSize.sp * lineHeight).toPx() * 6f })
        val contentHorizontalPaddingPx = with(density) { (24.dp + 24.dp).toPx() }
        val fontSizePx = with(density) { fontSize.sp.toPx() }
        val pages = remember(
            paragraphs,
            viewportWidthPx,
            viewportHeightPx,
            contentHorizontalPaddingPx,
            fontSizePx,
            lineHeight,
            isBilingualMode
        ) {
            buildReaderPages(
                paragraphs = paragraphs,
                viewportWidthPx = viewportWidthPx,
                viewportHeightPx = viewportHeightPx,
                contentHorizontalPaddingPx = contentHorizontalPaddingPx,
                fontSizePx = fontSizePx,
                lineHeight = lineHeight,
                isBilingualMode = isBilingualMode
            )
        }
        val paragraphToPage = remember(pages) {
            buildParagraphToPageMap(pages = pages)
        }
        val anchorTargets = remember(paragraphs) { buildAnchorTargetsMap(paragraphs) }
        val anchorToPage = remember(pages, anchorTargets) {
            buildAnchorToPageMap(
                pages = pages,
                anchorTargets = anchorTargets
            )
        }

        val pagerState = rememberPagerState(
            initialPage = 0,
            pageCount = { pages.size.coerceAtLeast(1) }
        )
        val maxImageHeight = maxHeight * 0.62f
        val tapZoneInteraction = remember { MutableInteractionSource() }

        LaunchedEffect(paragraphs) {
            if (pagerState.currentPage != 0) pagerState.scrollToPage(0)
        }

        LaunchedEffect(paragraphToPage) {
            onParagraphPageMapChanged?.invoke(paragraphToPage)
        }

        LaunchedEffect(pagerState, pages, paragraphs, isBilingualMode) {
            snapshotFlow { pagerState.currentPage }
                .distinctUntilChanged()
                .collect { pageRaw ->
                    val safePage = pageRaw.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
                    val totalPages = pages.size.coerceAtLeast(1)
                    val progress = if (totalPages <= 1) 0f else safePage.toFloat() / (totalPages - 1).toFloat()
                    val page = pages.getOrNull(safePage)
                    onProgressChanged(safePage, totalPages, progress)

                    if (isBilingualMode && page != null) {
                        val visible = buildVisiblePairs(page)
                        if (visible.isNotEmpty()) onParagraphsVisible(visible)
                    }
                }
        }

        LaunchedEffect(isBilingualMode, pages, paragraphs) {
            if (!isBilingualMode) return@LaunchedEffect
            val first = pages.firstOrNull() ?: return@LaunchedEffect
            val visible = buildVisiblePairs(first)
            if (visible.isNotEmpty()) onParagraphsVisible(visible)
        }

        LaunchedEffect(seekCommandId, seekParagraphIndex, seekProgress, pages, paragraphToPage) {
            if (seekCommandId == 0 || seekCommandId == lastHandledSeekCommandId || pages.isEmpty()) return@LaunchedEffect
            val target = when {
                seekParagraphIndex != null -> paragraphToPage[seekParagraphIndex] ?: 0
                seekProgress != null -> {
                    val p = seekProgress.coerceIn(0f, 1f)
                    (p * pages.lastIndex.toFloat()).roundToInt()
                }
                else -> pagerState.currentPage
            }.coerceIn(0, pages.lastIndex)
            pagerState.animateScrollToPage(target)
            lastHandledSeekCommandId = seekCommandId
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clickable(
                        interactionSource = tapZoneInteraction,
                        indication = null
                    ) { onSingleTap?.invoke() }
            ) {}

            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                HorizontalPager(
                    state = pagerState,
                    beyondBoundsPageCount = 1,
                    userScrollEnabled = pages.size > 1,
                    modifier = Modifier.fillMaxSize()
                ) { pageIndex ->
                    val page = pages.getOrNull(pageIndex)
                    if (page == null) {
                        Spacer(modifier = Modifier.fillMaxSize())
                        return@HorizontalPager
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.Top
                    ) {
                        page.slices.forEach { slice ->
                            NativeParagraphBlock(
                                slice = slice,
                                fontSize = fontSize,
                                lineHeight = lineHeight,
                                textColor = textColor,
                                maxImageHeight = maxImageHeight,
                                isBilingualMode = isBilingualMode,
                                translatedText = if (slice.isParagraphStart) {
                                    translations[slice.paragraphIndex]?.translatedText.orEmpty()
                                } else {
                                    ""
                                },
                                context = context,
                                onAnchorClick = { rawAnchor ->
                                    val anchor = normalizeAnchorKey(rawAnchor)
                                    if (anchor.isBlank()) return@NativeParagraphBlock
                                    val targetPage = resolveAnchorPage(
                                        anchorToPage = anchorToPage,
                                        paragraphToPage = paragraphToPage,
                                        anchorTargets = anchorTargets,
                                        anchor = anchor
                                    )
                                    targetPage
                                        ?: return@NativeParagraphBlock
                                    scope.launch { pagerState.animateScrollToPage(targetPage.coerceIn(0, pages.lastIndex)) }
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(28.dp))
                    }
                }

            }
        }
    }
}

@Composable
private fun NativeParagraphBlock(
    slice: ReaderSlice,
    fontSize: Float,
    lineHeight: Float,
    textColor: Color,
    maxImageHeight: Dp,
    isBilingualMode: Boolean,
    translatedText: String,
    context: android.content.Context,
    onAnchorClick: (String) -> Unit
) {
    val imageModel = remember(slice.imageSrc) {
        resolveImageModel(slice.imageSrc)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (slice.isHeading) 10.dp else 6.dp)
    ) {
        if (imageModel != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageModel)
                    .crossfade(true)
                    .build(),
                contentDescription = slice.text.ifBlank { null },
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxImageHeight)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
            )
            if (slice.text.isNotBlank()) {
                Text(
                    text = slice.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    TextView(ctx).apply {
                        movementMethod = LinkMovementMethod.getInstance()
                        linksClickable = true
                        setTextIsSelectable(false)
                        includeFontPadding = false
                    }
                },
                update = { tv ->
                    tv.textSize = if (slice.isHeading) fontSize + 8f else fontSize
                    tv.setLineSpacing(0f, if (slice.isHeading) 1.2f else lineHeight)
                    tv.setTextColor(textColor.toArgb())
                    val richText = slice.styledText ?: run {
                        val htmlSource = slice.html.ifBlank {
                            "<p>${TextUtils.htmlEncode(slice.text)}</p>"
                        }
                        HtmlCompat.fromHtml(htmlSource, HtmlCompat.FROM_HTML_MODE_COMPACT)
                    }
                    tv.text = makeClickableRichText(richText, context, onAnchorClick)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (isBilingualMode && slice.isParagraphStart && translatedText.isNotBlank()) {
            Text(
                text = translatedText,
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

private data class ReaderSlice(
    val paragraphPosition: Int,
    val paragraphIndex: Int,
    val text: String,
    val isHeading: Boolean,
    val html: String = "",
    val styledText: CharSequence? = null,
    val imageSrc: String? = null,
    val isParagraphStart: Boolean = true,
    val startCharOffset: Int = 0,
    val endCharOffset: Int = 0
)

private data class ReaderPage(val slices: List<ReaderSlice>)
private data class SliceMeasure(val slice: ReaderSlice, val heightPx: Float)
private data class SpannedChunk(val content: CharSequence, val start: Int, val end: Int)
private data class AnchorTarget(val paragraphIndex: Int, val charOffset: Int)

private fun buildReaderPages(
    paragraphs: List<ReaderParagraph>,
    viewportWidthPx: Float,
    viewportHeightPx: Float,
    contentHorizontalPaddingPx: Float,
    fontSizePx: Float,
    lineHeight: Float,
    isBilingualMode: Boolean
): List<ReaderPage> {
    if (paragraphs.isEmpty()) return emptyList()

    val contentWidth = (viewportWidthPx - contentHorizontalPaddingPx).coerceAtLeast(fontSizePx * 14f)
    val contentHeight = viewportHeightPx.coerceAtLeast(fontSizePx * 6f)
    val pageHeightBudget = if (isBilingualMode) contentHeight * 0.58f else contentHeight * 0.96f
    val slicesWithHeight = buildReaderSlices(
        paragraphs = paragraphs,
        contentWidthPx = contentWidth,
        contentHeightPx = contentHeight,
        fontSizePx = fontSizePx,
        lineHeight = lineHeight
    )

    val pages = mutableListOf<List<ReaderSlice>>()
    var current = mutableListOf<ReaderSlice>()
    var currentHeight = 0f
    val flushPage = {
        if (current.isNotEmpty()) {
            pages += current.toList()
            current = mutableListOf()
            currentHeight = 0f
        }
    }

    slicesWithHeight.forEach { (slice, rawHeight) ->
        val height = rawHeight.coerceAtLeast(fontSizePx * 1.2f)
        if (current.isNotEmpty() && currentHeight + height > pageHeightBudget) {
            flushPage()
        }
        current += slice
        currentHeight += height
        if (currentHeight >= pageHeightBudget * 0.985f) {
            flushPage()
        }
    }
    flushPage()
    return pages.map { ReaderPage(slices = it) }
}

private fun buildReaderSlices(
    paragraphs: List<ReaderParagraph>,
    contentWidthPx: Float,
    contentHeightPx: Float,
    fontSizePx: Float,
    lineHeight: Float
): List<SliceMeasure> {
    val result = mutableListOf<SliceMeasure>()
    val widthPx = contentWidthPx.roundToInt().coerceAtLeast(120)
    val bodyTextSizePx = fontSizePx
    val headingTextSizePx = fontSizePx * 1.5f
    val bodyVerticalPaddingPx = fontSizePx * 0.72f
    val headingVerticalPaddingPx = fontSizePx * 1.2f
    val maxImageHeightPx = contentHeightPx * 0.62f

    paragraphs.forEachIndexed { pos, paragraph ->
        if (!paragraph.imageSrc.isNullOrBlank()) {
            val captionHeight = if (paragraph.text.isBlank()) 0f else bodyTextSizePx * lineHeight * 1.2f
            val imageHeight = maxImageHeightPx + captionHeight + bodyVerticalPaddingPx
            result += SliceMeasure(
                slice = ReaderSlice(
                    paragraphPosition = pos,
                    paragraphIndex = paragraph.index,
                    text = paragraph.text,
                    isHeading = paragraph.isHeading,
                    imageSrc = paragraph.imageSrc,
                    startCharOffset = 0,
                    endCharOffset = 0
                ),
                heightPx = imageHeight
            )
            return@forEachIndexed
        }

        val htmlSource = paragraph.html.ifBlank {
            "<p>${TextUtils.htmlEncode(paragraph.text)}</p>"
        }
        val spanned = HtmlCompat.fromHtml(htmlSource, HtmlCompat.FROM_HTML_MODE_COMPACT)
        if (spanned.isEmpty()) return@forEachIndexed

        val textSizePx = if (paragraph.isHeading) headingTextSizePx else bodyTextSizePx
        val spacingMult = if (paragraph.isHeading) 1.2f else lineHeight
        val lineHeightPx = (textSizePx * spacingMult).coerceAtLeast(12f)
        val maxLinesPerSlice = (
            (contentHeightPx * if (paragraph.isHeading) 0.78f else 0.86f) / lineHeightPx
            ).toInt().coerceAtLeast(if (paragraph.isHeading) 4 else 8)
        val chunks = splitSpannedByLineBudget(
            text = spanned,
            textSizePx = textSizePx,
            lineSpacingMultiplier = spacingMult,
            widthPx = widthPx,
            maxLinesPerSlice = maxLinesPerSlice
        )

        chunks.forEachIndexed chunkLoop@ { chunkIndex, chunk ->
            val chunkText = chunk.content.toString()
                .replace(Regex("\\s+"), " ")
                .trim()
            if (chunkText.isBlank()) return@chunkLoop

            val textHeight = measureSpannedHeight(
                text = chunk.content,
                textSizePx = textSizePx,
                lineSpacingMultiplier = spacingMult,
                widthPx = widthPx
            )
            val verticalPadding = if (paragraph.isHeading) headingVerticalPaddingPx else bodyVerticalPaddingPx
            result += SliceMeasure(
                slice = ReaderSlice(
                    paragraphPosition = pos,
                    paragraphIndex = paragraph.index,
                    text = chunkText,
                    isHeading = paragraph.isHeading,
                    styledText = chunk.content,
                    isParagraphStart = chunkIndex == 0,
                    startCharOffset = chunk.start,
                    endCharOffset = chunk.end
                ),
                heightPx = textHeight + verticalPadding
            )
        }
    }
    return result
}

private fun splitSpannedByLineBudget(
    text: CharSequence,
    textSizePx: Float,
    lineSpacingMultiplier: Float,
    widthPx: Int,
    maxLinesPerSlice: Int
): List<SpannedChunk> {
    if (text.isEmpty()) return emptyList()
    val layout = buildStaticLayout(
        text = text,
        textSizePx = textSizePx,
        lineSpacingMultiplier = lineSpacingMultiplier,
        widthPx = widthPx
    )
    if (layout.lineCount <= maxLinesPerSlice) {
        return listOf(
            SpannedChunk(
                content = text,
                start = 0,
                end = text.length
            )
        )
    }

    val chunks = mutableListOf<SpannedChunk>()
    var startLine = 0
    while (startLine < layout.lineCount) {
        val endLineExclusive = (startLine + maxLinesPerSlice).coerceAtMost(layout.lineCount)
        var start = layout.getLineStart(startLine).coerceAtLeast(0)
        var end = layout.getLineEnd(endLineExclusive - 1).coerceAtMost(text.length)
        while (start < end && text[start].isWhitespace()) start += 1
        while (end > start && text[end - 1].isWhitespace()) end -= 1
        if (end > start) {
            chunks += SpannedChunk(
                content = SpannableStringBuilder(text, start, end),
                start = start,
                end = end
            )
        }
        startLine = endLineExclusive
    }
    return chunks
}

private fun measureSpannedHeight(
    text: CharSequence,
    textSizePx: Float,
    lineSpacingMultiplier: Float,
    widthPx: Int
): Float {
    if (text.isEmpty()) return 0f
    return buildStaticLayout(
        text = text,
        textSizePx = textSizePx,
        lineSpacingMultiplier = lineSpacingMultiplier,
        widthPx = widthPx
    ).height.toFloat()
}

private fun buildStaticLayout(
    text: CharSequence,
    textSizePx: Float,
    lineSpacingMultiplier: Float,
    widthPx: Int
): StaticLayout {
    val paint = TextPaint().apply {
        this.textSize = textSizePx
        isAntiAlias = true
    }
    return StaticLayout.Builder.obtain(
        text,
        0,
        text.length,
        paint,
        widthPx.coerceAtLeast(80)
    )
        .setIncludePad(false)
        .setLineSpacing(0f, lineSpacingMultiplier)
        .build()
}

private fun buildParagraphToPageMap(
    pages: List<ReaderPage>
): Map<Int, Int> {
    val map = mutableMapOf<Int, Int>()
    pages.forEachIndexed { pageIndex, page ->
        page.slices.forEach { slice ->
            map.putIfAbsent(slice.paragraphIndex, pageIndex)
        }
    }
    return map
}

private fun buildVisiblePairs(page: ReaderPage): List<Pair<Int, String>> {
    return page.slices
        .asSequence()
        .filter { it.isParagraphStart && it.imageSrc.isNullOrBlank() }
        .mapNotNull { slice ->
            val text = slice.text.trim()
            if (text.isBlank()) return@mapNotNull null
            slice.paragraphIndex to text
        }
        .distinctBy { it.first }
        .toList()
}

private fun makeClickableRichText(
    text: CharSequence,
    context: android.content.Context,
    onAnchorClick: (String) -> Unit
): CharSequence {
    if (text !is Spanned) return text
    val spannable = SpannableStringBuilder(text)
    val spans = spannable.getSpans(0, spannable.length, URLSpan::class.java)
    spans.forEach { span ->
        val start = spannable.getSpanStart(span)
        val end = spannable.getSpanEnd(span)
        val flags = spannable.getSpanFlags(span)
        val url = span.url.orEmpty()
        spannable.removeSpan(span)
        spannable.setSpan(
            object : ClickableSpan() {
                override fun onClick(widget: View) {
                    val internalAnchor = extractInternalAnchor(url)
                    if (!internalAnchor.isNullOrBlank()) {
                        onAnchorClick(internalAnchor)
                        return
                    }
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    }
                }
            },
            start,
            end,
            flags
        )
    }
    return spannable
}

private fun extractInternalAnchor(url: String): String? {
    val raw = url.trim()
    if (raw.isBlank()) return null
    val decoded = runCatching {
        URLDecoder.decode(raw, Charsets.UTF_8.name())
    }.getOrDefault(raw)
    if (decoded.startsWith("#")) {
        val fragment = normalizeAnchorKey(decoded)
        return fragment.ifBlank { null }
    }
    val hashIndex = decoded.indexOf('#')
    if (hashIndex < 0 || hashIndex >= decoded.lastIndex) return null
    val scheme = decoded.substringBefore(':', missingDelimiterValue = "").lowercase()
    val isExternalScheme = scheme in setOf(
        "http",
        "https",
        "mailto",
        "tel",
        "javascript",
        "data"
    )
    if (isExternalScheme) return null
    val fragment = normalizeAnchorKey(decoded.substring(hashIndex + 1))
    return fragment.ifBlank { null }
}

private fun resolveImageModel(imageSrc: String?): Any? {
    val src = imageSrc?.trim().orEmpty()
    if (src.isBlank()) return null
    if (!src.startsWith("data:", ignoreCase = true)) return src
    return decodeDataUri(src) ?: src
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

private fun normalizeAnchorKey(rawAnchor: String): String {
    val fragment = rawAnchor.substringAfter('#', rawAnchor).trim()
    if (fragment.isBlank()) return ""
    val decoded = runCatching {
        URLDecoder.decode(fragment, Charsets.UTF_8.name())
    }.getOrDefault(fragment)
    return decoded.removePrefix("#").trim()
}

private const val ANCHOR_MARKER_TOKEN = "\uE000\uE001"

private fun buildAnchorTargetsMap(paragraphs: List<ReaderParagraph>): Map<String, AnchorTarget> {
    val map = linkedMapOf<String, AnchorTarget>()
    paragraphs.forEachIndexed { pos, paragraph ->
        val html = paragraph.html
        if (html.isBlank()) return@forEachIndexed
        if (!html.contains("id=", ignoreCase = true) && !html.contains("name=", ignoreCase = true)) {
            return@forEachIndexed
        }
        val paragraphTarget = if (
            paragraph.text.isBlank() &&
            paragraph.imageSrc.isNullOrBlank()
        ) {
            paragraphs
                .drop(pos + 1)
                .firstOrNull { it.text.isNotBlank() || !it.imageSrc.isNullOrBlank() }
                ?.index
                ?: paragraph.index
        } else {
            paragraph.index
        }
        val anchorOffsets = extractAnchorOffsetsFromHtml(html)
        anchorOffsets.forEach { (anchorKey, offset) ->
            if (anchorKey.isBlank()) return@forEach
            val target = AnchorTarget(
                paragraphIndex = paragraphTarget,
                charOffset = if (paragraphTarget == paragraph.index) {
                    offset.coerceAtLeast(0)
                } else {
                    0
                }
            )
            map.putIfAbsent(anchorKey, target)
            map.putIfAbsent(anchorKey.lowercase(), target)
        }
    }
    return map
}

private fun extractAnchorOffsetsFromHtml(html: String): Map<String, Int> {
    val doc = Jsoup.parseBodyFragment(html)
    val markerOrdersByKey = linkedMapOf<String, Int>()
    var markerOrder = 0
    doc.select("[id],a[name]").forEach { element ->
        val keys = collectAnchorKeys(element)
        if (keys.isEmpty()) return@forEach
        element.before(TextNode(ANCHOR_MARKER_TOKEN))
        keys.forEach { key ->
            if (key.isNotBlank()) {
                markerOrdersByKey.putIfAbsent(key, markerOrder)
                markerOrdersByKey.putIfAbsent(key.lowercase(), markerOrder)
            }
        }
        markerOrder += 1
    }
    if (markerOrdersByKey.isEmpty()) return emptyMap()

    val markedSpanned = HtmlCompat.fromHtml(
        doc.body().html(),
        HtmlCompat.FROM_HTML_MODE_COMPACT
    )
    val markerText = markedSpanned.toString()
    val markerPositions = mutableListOf<Int>()
    var index = markerText.indexOf(ANCHOR_MARKER_TOKEN)
    while (index >= 0) {
        markerPositions += index
        index = markerText.indexOf(
            ANCHOR_MARKER_TOKEN,
            startIndex = index + ANCHOR_MARKER_TOKEN.length
        )
    }
    if (markerPositions.isEmpty()) {
        return markerOrdersByKey.mapValues { 0 }
    }

    val offsets = linkedMapOf<String, Int>()
    markerOrdersByKey.forEach { (key, order) ->
        val markerPosition = markerPositions.getOrNull(order)
        val offset = if (markerPosition != null) {
            (markerPosition - order * ANCHOR_MARKER_TOKEN.length).coerceAtLeast(0)
        } else {
            0
        }
        offsets.putIfAbsent(key, offset)
    }
    return offsets
}

private fun collectAnchorKeys(element: org.jsoup.nodes.Element): List<String> {
    val keys = mutableListOf<String>()
    val id = normalizeAnchorKey(element.id())
    if (id.isNotBlank()) keys += id
    val name = normalizeAnchorKey(element.attr("name"))
    if (name.isNotBlank()) keys += name
    return keys
}

private fun buildAnchorToPageMap(
    pages: List<ReaderPage>,
    anchorTargets: Map<String, AnchorTarget>
): Map<String, Int> {
    if (pages.isEmpty() || anchorTargets.isEmpty()) return emptyMap()
    val map = linkedMapOf<String, Int>()
    anchorTargets.forEach { (anchor, target) ->
        val page = findPageForAnchorTarget(pages = pages, target = target) ?: return@forEach
        map.putIfAbsent(anchor, page)
        map.putIfAbsent(anchor.lowercase(), page)
    }
    return map
}

private fun findPageForAnchorTarget(
    pages: List<ReaderPage>,
    target: AnchorTarget
): Int? {
    var firstParagraphPage: Int? = null
    var lastParagraphPage: Int? = null
    for (pageIndex in pages.indices) {
        val slices = pages[pageIndex].slices
        for (slice in slices) {
            if (slice.paragraphIndex != target.paragraphIndex) continue
            if (firstParagraphPage == null) firstParagraphPage = pageIndex
            lastParagraphPage = pageIndex

            if (!slice.imageSrc.isNullOrBlank()) continue
            val start = slice.startCharOffset.coerceAtLeast(0)
            val end = slice.endCharOffset.coerceAtLeast(start)
            if (target.charOffset < start) return pageIndex

            val inSlice = if (end > start) {
                target.charOffset in start until end
            } else {
                target.charOffset <= start
            }
            if (inSlice) return pageIndex
        }
    }
    return lastParagraphPage ?: firstParagraphPage
}

private fun resolveAnchorPage(
    anchorToPage: Map<String, Int>,
    paragraphToPage: Map<Int, Int>,
    anchorTargets: Map<String, AnchorTarget>,
    anchor: String
): Int? {
    anchorToPage[anchor]?.let { return it }
    anchorToPage[anchor.lowercase()]?.let { return it }

    val suffixPageMatches = anchorToPage.entries
        .asSequence()
        .filter { (key, _) -> key.endsWith("__$anchor", ignoreCase = true) }
        .map { it.value }
        .distinct()
        .toList()
    if (suffixPageMatches.size == 1) return suffixPageMatches.first()

    anchorTargets[anchor]?.let { return paragraphToPage[it.paragraphIndex] }
    anchorTargets[anchor.lowercase()]?.let { return paragraphToPage[it.paragraphIndex] }

    val suffixParagraphMatches = anchorTargets.entries
        .asSequence()
        .filter { (key, _) -> key.endsWith("__$anchor", ignoreCase = true) }
        .map { it.value.paragraphIndex }
        .distinct()
        .toList()
    if (suffixParagraphMatches.size == 1) {
        return paragraphToPage[suffixParagraphMatches.first()]
    }
    return null
}
