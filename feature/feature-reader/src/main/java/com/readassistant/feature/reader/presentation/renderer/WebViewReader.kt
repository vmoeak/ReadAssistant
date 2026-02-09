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

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewReader(htmlContent: String, themeType: ReadingThemeType, fontSize: Float, lineHeight: Float, isBilingualMode: Boolean, onTextSelected: (TextSelection) -> Unit, onProgressChanged: (Float) -> Unit, modifier: Modifier = Modifier) {
    val css = generateReaderCss(themeType, fontSize, lineHeight)
    val html = remember(htmlContent, css) { "<!DOCTYPE html><html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1.0\"><style>$css</style></head><body><div id=\"reader-content\">$htmlContent</div><script>document.querySelectorAll('#reader-content p,#reader-content h1,#reader-content h2,#reader-content h3,#reader-content li,#reader-content blockquote').forEach(function(p,i){p.setAttribute('data-para-idx',i)});document.addEventListener('selectionchange',function(){var s=window.getSelection();if(s&&s.toString().trim().length>0){var r=s.getRangeAt(0);var rect=r.getBoundingClientRect();var p=r.startContainer.parentElement;while(p&&!p.getAttribute('data-para-idx'))p=p.parentElement;Android.onTextSelected(s.toString(),r.startOffset,r.endOffset,p?parseInt(p.getAttribute('data-para-idx')):-1,rect.left,rect.top,rect.right,rect.bottom)}});var st;window.addEventListener('scroll',function(){clearTimeout(st);st=setTimeout(function(){var t=window.pageYOffset;var h=document.documentElement.scrollHeight-window.innerHeight;Android.onScrollProgress(h>0?t/h:0)},200)});</script></body></html>" }
    AndroidView(factory = { ctx ->
        WebView(ctx).apply {
            settings.javaScriptEnabled = true; settings.domStorageEnabled = true
            addJavascriptInterface(object { @JavascriptInterface fun onTextSelected(text: String, s: Int, e: Int, p: Int, l: Float, t: Float, r: Float, b: Float) { onTextSelected(TextSelection(text, s, e, p, SelectionRect(l, t, r, b))) }; @JavascriptInterface fun onScrollProgress(p: Float) { onProgressChanged(p) } }, "Android")
            webViewClient = WebViewClient()
        }
    }, update = { it.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null) }, modifier = modifier)
}

fun generateReaderCss(t: ReadingThemeType, fs: Float, lh: Float): String {
    val (bg, fg, link) = when (t) { ReadingThemeType.LIGHT -> Triple("#FFF","#1A1A1A","#4A90D9"); ReadingThemeType.SEPIA -> Triple("#FBF0D9","#3E2723","#8D6E63"); ReadingThemeType.DARK -> Triple("#1A1A2E","#E8E8E8","#64B5F6") }
    return "* {box-sizing:border-box} body {background:$bg;color:$fg;font-family:-apple-system,sans-serif;font-size:${fs}px;line-height:$lh;padding:16px 24px;margin:0 auto;max-width:720px;word-wrap:break-word} a{color:$link} img{max-width:100%;height:auto} pre,code{background:${if(t==ReadingThemeType.DARK)"#0F3460" else "#F5F5F5"};padding:2px 6px;border-radius:4px;font-size:0.9em} blockquote{border-left:3px solid $link;margin-left:0;padding-left:16px} .translation{color:${if(t==ReadingThemeType.DARK)"#90A4AE" else "#666"};font-style:italic;background:${if(t==ReadingThemeType.DARK)"rgba(255,255,255,0.05)" else "rgba(0,0,0,0.03)"};border-left:3px solid $link;padding:8px 12px;margin:4px 0 16px 0}"
}
