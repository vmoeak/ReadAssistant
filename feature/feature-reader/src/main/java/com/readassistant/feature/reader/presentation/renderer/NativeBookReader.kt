package com.readassistant.feature.reader.presentation.renderer

import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
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
    val density = LocalDensity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var lastHandledSeekCommandId by rememberSaveable { mutableIntStateOf(0) }

    BoxWithConstraints(modifier = modifier) {
        val statusBarTopPadding = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
        val navBarBottomPadding = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
        val topPadding = statusBarTopPadding + 24.dp
        val bottomPadding = navBarBottomPadding + 8.dp

        val topContentPaddingPx = with(density) { topPadding.toPx() }
        val bottomContentPaddingPx = with(density) { bottomPadding.toPx() }
        val paragraphGapPx = with(density) { 4.dp.toPx() }
        val viewportWidthPx = with(density) { maxWidth.toPx() }
        val viewportHeightPx = with(density) { maxHeight.toPx() }
        val horizontalPaddingPx = with(density) { 24.dp.toPx() * 2f }
        val contentWidthPx = (viewportWidthPx - horizontalPaddingPx).coerceAtLeast(220f)
        val contentHeightPx = (viewportHeightPx - topContentPaddingPx - bottomContentPaddingPx).coerceAtLeast(300f)
        val maxImageHeightPx = (contentHeightPx * 0.78f).coerceAtLeast(with(density) { 220.dp.toPx() })
        val maxImageHeightDp = with(density) { maxImageHeightPx.toDp() }

        val entries = remember(paragraphs) { normalizeEntries(paragraphs) }
        val pages = remember(entries, contentWidthPx, contentHeightPx, fontSize, lineHeight, isBilingualMode) {
            paginateEntries(
                entries = entries,
                contentWidthPx = contentWidthPx,
                contentHeightPx = if (isBilingualMode) contentHeightPx * 0.92f else contentHeightPx,
                fontSizePx = with(density) { fontSize.sp.toPx() },
                lineHeight = lineHeight,
                maxImageHeightPx = maxImageHeightPx,
                paragraphGapPx = paragraphGapPx
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
                seekParagraphIndex != null -> paragraphToPageMap[seekParagraphIndex] ?: 0
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
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                ) {
                    page.items.forEachIndexed { idx, item ->
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
                                SubcomposeAsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(model)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = item.text.ifBlank { null },
                                    contentScale = ContentScale.Fit,
                                    modifier = imageModifier
                                ) {
                                    when (painter.state) {
                                        is AsyncImagePainter.State.Error -> {
                                            Text(
                                                text = "Image unavailable",
                                                style = TextStyle(
                                                    fontSize = (fontSize - 1f).coerceAtLeast(12f).sp,
                                                    lineHeight = (((fontSize - 1f).coerceAtLeast(12f)) * lineHeight).sp,
                                                    color = secondaryTextColor
                                                )
                                            )
                                        }
                                        else -> SubcomposeAsyncImageContent()
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
                            Text(
                                text = item.text,
                                style = textStyle,
                                maxLines = Int.MAX_VALUE,
                                overflow = TextOverflow.Clip
                            )
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
            }
        }
    }
}

private data class NormalizedEntry(
    val paragraphIndex: Int,
    val text: String,
    val isHeading: Boolean,
    val isParagraphStart: Boolean,
    val imageSrc: String? = null
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
            return@mapNotNull NormalizedEntry(
                paragraphIndex = paragraph.index,
                text = normalizedCaption,
                isHeading = false,
                isParagraphStart = true,
                imageSrc = paragraph.imageSrc
            )
        }

        val text = when {
            paragraph.html.isNotBlank() -> Jsoup.parse(paragraph.html).text().replace(Regex("\\s+"), " ").trim()
            else -> paragraph.text.replace(Regex("\\s+"), " ").trim()
        }

        if (text.isBlank()) return@mapNotNull null
        if (!paragraph.isHeading && isGenericImageLabel(text)) return@mapNotNull null
        NormalizedEntry(
            paragraphIndex = paragraph.index,
            text = text,
            isHeading = paragraph.isHeading,
            isParagraphStart = true,
            imageSrc = null
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

private fun paginateEntries(
    entries: List<NormalizedEntry>,
    contentWidthPx: Float,
    contentHeightPx: Float,
    fontSizePx: Float,
    lineHeight: Float,
    maxImageHeightPx: Float,
    paragraphGapPx: Float
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
                paragraphGapPx = paragraphGapPx
            )

            if (current.isNotEmpty() && currentHeight + pendingHeight > contentHeightPx) {
                val remaining = (contentHeightPx - currentHeight).coerceAtLeast(fontSizePx * 2.1f)
                val split = splitEntryByHeight(
                    entry = pending,
                    widthPx = contentWidthPx,
                    availableHeightPx = remaining,
                    fontSizePx = fontSizePx,
                    lineHeight = lineHeight
                )
                if (split != null) {
                    current += split.first
                    flush()
                    pending = split.second
                    continue
                }
                flush()
            }

            if (current.isEmpty() && pendingHeight > contentHeightPx) {
                val split = splitEntryByHeight(
                    entry = pending,
                    widthPx = contentWidthPx,
                    availableHeightPx = contentHeightPx,
                    fontSizePx = fontSizePx,
                    lineHeight = lineHeight
                )
                if (split != null) {
                    current += split.first
                    flush()
                    pending = split.second
                    continue
                }
            }

            current += pending
            currentHeight += pendingHeight
            break
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
    val spacingMult = if (entry.isHeading) 1.22f else lineHeight
    val layout = buildStaticLayout(
        text = entry.text,
        textSizePx = textSizePx,
        lineSpacingMultiplier = spacingMult,
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

    val head = entry.copy(text = headText)
    val tail = entry.copy(text = tailText, isParagraphStart = false)
    return head to tail
}

private fun measureEntryHeight(
    entry: NormalizedEntry,
    widthPx: Float,
    fontSizePx: Float,
    lineHeight: Float,
    maxImageHeightPx: Float,
    paragraphGapPx: Float
): Float {
    if (!entry.imageSrc.isNullOrBlank()) {
        val captionHeight = if (entry.text.isBlank()) 0f else fontSizePx * lineHeight * 1.2f
        return (maxImageHeightPx * 0.72f) + captionHeight + paragraphGapPx
    }

    val textSizePx = if (entry.isHeading) fontSizePx * 1.35f else fontSizePx
    val spacingMult = if (entry.isHeading) 1.22f else lineHeight
    val layout = buildStaticLayout(
        text = entry.text,
        textSizePx = textSizePx,
        lineSpacingMultiplier = spacingMult,
        widthPx = widthPx.roundToInt().coerceAtLeast(120)
    )
    return layout.height.toFloat() + paragraphGapPx
}

private fun buildStaticLayout(
    text: CharSequence,
    textSizePx: Float,
    lineSpacingMultiplier: Float,
    widthPx: Int
): StaticLayout {
    val paint = TextPaint().apply {
        textSize = textSizePx
        isAntiAlias = true
    }
    return StaticLayout.Builder.obtain(text, 0, text.length, paint, widthPx)
        .setIncludePad(false)
        .setLineSpacing(0f, lineSpacingMultiplier)
        .build()
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
