package com.readassistant.feature.reader.presentation.renderer

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.readassistant.core.domain.model.TextSelection
import com.readassistant.core.domain.model.SelectionRect
import com.readassistant.core.ui.theme.ReadingThemeType
import com.readassistant.feature.translation.domain.TranslationPair

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewReader(
    htmlContent: String, themeType: ReadingThemeType, fontSize: Float, lineHeight: Float,
    isBilingualMode: Boolean, translations: Map<Int, TranslationPair>,
    onTextSelected: (TextSelection) -> Unit, onProgressChanged: (Float) -> Unit,
    onParagraphsExtracted: (List<Pair<Int, String>>) -> Unit,
    modifier: Modifier = Modifier
) {
    val css = generateReaderCss(themeType, fontSize, lineHeight)
    val html = remember(htmlContent, css) {
        """<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1.0"><style>$css</style></head><body><div id="reader-content">$htmlContent</div><script>
var paras=document.querySelectorAll('#reader-content p,#reader-content h1,#reader-content h2,#reader-content h3,#reader-content h4,#reader-content li,#reader-content blockquote');
paras.forEach(function(p,i){p.setAttribute('data-para-idx',i)});
document.addEventListener('selectionchange',function(){var s=window.getSelection();if(s&&s.toString().trim().length>0){try{var r=s.getRangeAt(0);var rect=r.getBoundingClientRect();var p=r.startContainer.parentElement;while(p&&!p.getAttribute('data-para-idx'))p=p.parentElement;Android.onTextSelected(s.toString(),r.startOffset,r.endOffset,p?parseInt(p.getAttribute('data-para-idx')):-1,rect.left,rect.top,rect.right,rect.bottom)}catch(e){}}});
var st;window.addEventListener('scroll',function(){clearTimeout(st);st=setTimeout(function(){var t=window.pageYOffset;var h=document.documentElement.scrollHeight-window.innerHeight;Android.onScrollProgress(h>0?t/h:0)},200)});
function extractParagraphs(){var r=[];paras.forEach(function(p,i){var t=p.textContent.trim();if(t.length>0)r.push(i+'||'+t)});Android.onParagraphsExtracted(r.join('@@SEP@@'))}
function setTranslation(idx,text){var p=document.querySelector('[data-para-idx="'+idx+'"]');if(!p)return;var t=p.nextElementSibling;if(!t||!t.classList.contains('translation')){t=document.createElement('div');t.className='translation';t.setAttribute('data-trans-idx',idx);p.parentNode.insertBefore(t,p.nextSibling)}t.textContent=text}
function removeAllTranslations(){document.querySelectorAll('.translation').forEach(function(e){e.remove()})}
</script></body></html>"""
    }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // When bilingual mode is toggled on, extract paragraphs; when off, remove translations
    LaunchedEffect(isBilingualMode) {
        val wv = webViewRef ?: return@LaunchedEffect
        if (isBilingualMode) {
            wv.evaluateJavascript("extractParagraphs()", null)
        } else {
            wv.evaluateJavascript("removeAllTranslations()", null)
        }
    }

    // Inject translations into WebView as they arrive
    LaunchedEffect(translations) {
        val wv = webViewRef ?: return@LaunchedEffect
        if (!isBilingualMode) return@LaunchedEffect
        translations.forEach { (idx, pair) ->
            val escaped = pair.translatedText.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
            wv.evaluateJavascript("setTranslation($idx,'$escaped')", null)
        }
    }

    AndroidView(factory = { ctx ->
        WebView(ctx).apply {
            settings.javaScriptEnabled = true; settings.domStorageEnabled = true
            addJavascriptInterface(object {
                @JavascriptInterface fun onTextSelected(text: String, s: Int, e: Int, p: Int, l: Float, t: Float, r: Float, b: Float) { onTextSelected(TextSelection(text, s, e, p, SelectionRect(l, t, r, b))) }
                @JavascriptInterface fun onScrollProgress(p: Float) { onProgressChanged(p) }
                @JavascriptInterface fun onParagraphsExtracted(data: String) {
                    if (data.isBlank()) return
                    val pairs = data.split("@@SEP@@").mapNotNull { entry ->
                        val parts = entry.split("||", limit = 2)
                        if (parts.size == 2) parts[0].toIntOrNull()?.let { it to parts[1] } else null
                    }
                    onParagraphsExtracted(pairs)
                }
            }, "Android")
            webViewClient = WebViewClient()
            webViewRef = this
        }
    }, update = { it.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null) }, modifier = modifier)
}

fun generateReaderCss(t: ReadingThemeType, fs: Float, lh: Float): String {
    val (bg, fg, link) = when (t) { ReadingThemeType.LIGHT -> Triple("#FFF","#1A1A1A","#4A90D9"); ReadingThemeType.SEPIA -> Triple("#FBF0D9","#3E2723","#8D6E63"); ReadingThemeType.DARK -> Triple("#1A1A2E","#E8E8E8","#64B5F6") }
    return "* {box-sizing:border-box} body {background:$bg;color:$fg;font-family:-apple-system,sans-serif;font-size:${fs}px;line-height:$lh;padding:16px 24px;margin:0 auto;max-width:720px;word-wrap:break-word} a{color:$link} img{max-width:100%;height:auto} pre,code{background:${if(t==ReadingThemeType.DARK)"#0F3460" else "#F5F5F5"};padding:2px 6px;border-radius:4px;font-size:0.9em} blockquote{border-left:3px solid $link;margin-left:0;padding-left:16px} .translation{color:${if(t==ReadingThemeType.DARK)"#90A4AE" else "#666"};font-style:italic;background:${if(t==ReadingThemeType.DARK)"rgba(255,255,255,0.05)" else "rgba(0,0,0,0.03)"};border-left:3px solid $link;padding:8px 12px;margin:4px 0 16px 0}"
}
