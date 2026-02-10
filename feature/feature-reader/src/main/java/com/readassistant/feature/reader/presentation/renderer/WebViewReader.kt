package com.readassistant.feature.reader.presentation.renderer

import android.annotation.SuppressLint
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
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
    pagedMode: Boolean = false,
    onSwipeLeft: (() -> Unit)? = null,
    onSwipeRight: (() -> Unit)? = null,
    onPageChanged: ((currentPage: Int, totalPages: Int, progress: Float) -> Unit)? = null,
    onChaptersExtracted: ((List<Pair<String, Int>>) -> Unit)? = null,
    seekCommandId: Int = 0,
    seekPage: Int? = null,
    seekProgress: Float? = null,
    onSingleTap: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val css = generateReaderCss(themeType, fontSize, lineHeight, pagedMode)
    val pagedFlag = if (pagedMode) "true" else "false"
    val html = remember(htmlContent, css) {
        """<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1.0"><style>$css</style></head><body><div id="reader-content">$htmlContent</div><div id="page-turn-overlay"></div><script>
var pagedMode=$pagedFlag;
var pageIndex=0;
var totalPages=1;
var pageStride=0;
var isAnimating=false;
var paras=document.querySelectorAll('#reader-content p,#reader-content h1,#reader-content h2,#reader-content h3,#reader-content h4,#reader-content li,#reader-content blockquote');
paras.forEach(function(p,i){p.setAttribute('data-para-idx',i)});
document.addEventListener('selectionchange',function(){var s=window.getSelection();if(s&&s.toString().trim().length>0){try{var r=s.getRangeAt(0);var rect=r.getBoundingClientRect();var p=r.startContainer.parentElement;while(p&&!p.getAttribute('data-para-idx'))p=p.parentElement;Android.onTextSelected(s.toString(),r.startOffset,r.endOffset,p?parseInt(p.getAttribute('data-para-idx')):-1,rect.left,rect.top,rect.right,rect.bottom)}catch(e){}}});
var st;window.addEventListener('scroll',function(){clearTimeout(st);st=setTimeout(function(){var t=window.pageYOffset;var h=document.documentElement.scrollHeight-window.innerHeight;Android.onScrollProgress(h>0?t/h:0)},200)});
function extractParagraphs(){var r=[];paras.forEach(function(p,i){var t=p.textContent.trim();if(t.length>0)r.push(i+'||'+t)});Android.onParagraphsExtracted(r.join('@@SEP@@'))}
function setTranslation(idx,text){var p=document.querySelector('[data-para-idx="'+idx+'"]');if(!p)return;var t=p.nextElementSibling;if(!t||!t.classList.contains('translation')){t=document.createElement('div');t.className='translation';t.setAttribute('data-trans-idx',idx);p.parentNode.insertBefore(t,p.nextSibling)}t.textContent=text}
function removeAllTranslations(){document.querySelectorAll('.translation').forEach(function(e){e.remove()})}
function emitChapters(){
  if(!pagedMode || pageStride<=0) return;
  var hs=document.querySelectorAll('#reader-content h1,#reader-content h2,#reader-content h3');
  if(!hs||hs.length===0){Android.onChaptersExtracted('');return;}
  var out=[];
  hs.forEach(function(h){
    var title=(h.textContent||'').trim();
    if(!title) return;
    var page=Math.max(0,Math.floor(h.offsetLeft/pageStride));
    title=title.replace(/\|\|/g,' ').replace(/@@SEP@@/g,' ');
    out.push(title+'||'+page);
  });
  Android.onChaptersExtracted(out.join('@@SEP@@'));
}
function updatePageMetrics(){
  if(!pagedMode) return;
  var c=document.getElementById('reader-content');
  if(!c) return;
  var viewportWidth=Math.max(1,window.innerWidth-48);
  var viewportHeight=Math.max(1,window.innerHeight-20);
  c.style.width=viewportWidth+'px';
  c.style.height=viewportHeight+'px';
  var gap=24;
  pageStride=viewportWidth+gap;
  if(pageStride<100){setTimeout(updatePageMetrics,120);return;}
  var totalScrollable=Math.max(0,c.scrollWidth-viewportWidth);
  totalPages=Math.max(1,Math.round(totalScrollable/pageStride)+1);
  if(pageIndex>totalPages-1) pageIndex=totalPages-1;
  c.scrollLeft=pageIndex*pageStride;
  var progress=totalPages<=1?1:(pageIndex/(totalPages-1));
  Android.onPageChanged(pageIndex,totalPages,progress);
  emitChapters();
}
function runTurnAnimation(direction, onMidpoint){
  if(!pagedMode || isAnimating) return false;
  var overlay=document.getElementById('page-turn-overlay');
  var content=document.getElementById('reader-content');
  if(!overlay){
    onMidpoint();
    return true;
  }
  isAnimating=true;
  overlay.classList.remove('turn-next','turn-prev','active');
  if(content) content.classList.remove('turning-next','turning-prev');
  overlay.classList.add(direction>0?'turn-next':'turn-prev');
  if(content) content.classList.add(direction>0?'turning-next':'turning-prev');
  requestAnimationFrame(function(){overlay.classList.add('active')});
  setTimeout(function(){onMidpoint()},160);
  setTimeout(function(){
    overlay.classList.remove('active','turn-next','turn-prev');
    if(content) content.classList.remove('turning-next','turning-prev');
    isAnimating=false;
  },320);
  return true;
}
function nextPage(){
  if(!pagedMode || isAnimating) return false;
  if(pageIndex>=totalPages-1) return false;
  return runTurnAnimation(1,function(){pageIndex+=1;updatePageMetrics();});
}
function prevPage(){
  if(!pagedMode || isAnimating) return false;
  if(pageIndex<=0) return false;
  return runTurnAnimation(-1,function(){pageIndex-=1;updatePageMetrics();});
}
function goToPage(target){
  if(!pagedMode) return false;
  var p=parseInt(target);
  if(isNaN(p)) return false;
  pageIndex=Math.max(0,Math.min(totalPages-1,p));
  updatePageMetrics();
  return true;
}
function goToProgress(v){
  if(!pagedMode) return false;
  var f=parseFloat(v);
  if(isNaN(f)) return false;
  f=Math.max(0,Math.min(1,f));
  pageIndex=Math.round((totalPages-1)*f);
  updatePageMetrics();
  return true;
}
window.addEventListener('load',function(){setTimeout(updatePageMetrics,80)});
window.addEventListener('resize',function(){setTimeout(updatePageMetrics,80)});
</script></body></html>"""
    }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val onSwipeLeftState by rememberUpdatedState(onSwipeLeft)
    val onSwipeRightState by rememberUpdatedState(onSwipeRight)
    val onPageChangedState by rememberUpdatedState(onPageChanged)
    val onChaptersExtractedState by rememberUpdatedState(onChaptersExtracted)
    val onSingleTapState by rememberUpdatedState(onSingleTap)
    var lastHandledSeekCommandId by remember { mutableStateOf(0) }

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
    LaunchedEffect(seekCommandId, seekPage, seekProgress) {
        val wv = webViewRef ?: return@LaunchedEffect
        if (seekCommandId == 0 || seekCommandId == lastHandledSeekCommandId) return@LaunchedEffect
        when {
            seekPage != null -> wv.evaluateJavascript("goToPage($seekPage)", null)
            seekProgress != null -> wv.evaluateJavascript("goToProgress($seekProgress)", null)
        }
        lastHandledSeekCommandId = seekCommandId
    }

    var lastLoadedHtml by remember { mutableStateOf("") }

    AndroidView(factory = { ctx ->
        WebView(ctx).apply {
            settings.javaScriptEnabled = true; settings.domStorageEnabled = true
            val gestureDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    onSingleTapState?.invoke()
                    return false
                }

                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    if (e1 == null) return false
                    val dx = e2.x - e1.x
                    val dy = e2.y - e1.y
                    val horizontalSwipe = kotlin.math.abs(dx) > kotlin.math.abs(dy)
                    val distanceEnough = kotlin.math.abs(dx) > 120f
                    val velocityEnough = kotlin.math.abs(velocityX) > 800f
                    if (horizontalSwipe && distanceEnough && velocityEnough) {
                        if (dx < 0) {
                            if (onSwipeLeftState != null) onSwipeLeftState?.invoke() else if (pagedMode) evaluateJavascript("nextPage()", null)
                        } else {
                            if (onSwipeRightState != null) onSwipeRightState?.invoke() else if (pagedMode) evaluateJavascript("prevPage()", null)
                        }
                        return true
                    }
                    return false
                }
            })
            setOnTouchListener { _, event ->
                gestureDetector.onTouchEvent(event)
                if (pagedMode && event.action == MotionEvent.ACTION_MOVE) return@setOnTouchListener true
                false
            }
            val actionModeCallback = object : ActionMode.Callback {
                override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean { menu?.clear(); return false }
                override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean { menu?.clear(); return false }
                override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean = false
                override fun onDestroyActionMode(mode: ActionMode?) {}
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                try {
                    val viewClass = android.view.View::class.java
                    val setSelectionMethod = viewClass.getMethod("setCustomSelectionActionModeCallback", android.view.ActionMode.Callback::class.java)
                    setSelectionMethod.invoke(this, actionModeCallback)
                    val setInsertionMethod = viewClass.getMethod("setCustomInsertionActionModeCallback", android.view.ActionMode.Callback::class.java)
                    setInsertionMethod.invoke(this, actionModeCallback)
                } catch (_: Exception) {}
            }
            addJavascriptInterface(object {
                @JavascriptInterface fun onTextSelected(text: String, s: Int, e: Int, p: Int, l: Float, t: Float, r: Float, b: Float) { onTextSelected(TextSelection(text, s, e, p, SelectionRect(l, t, r, b))) }
                @JavascriptInterface fun onScrollProgress(p: Float) { onProgressChanged(p) }
                @JavascriptInterface fun onPageChanged(currentPage: Int, total: Int, progress: Float) { onPageChangedState?.invoke(currentPage, total, progress) }
                @JavascriptInterface fun onChaptersExtracted(data: String) {
                    if (data.isBlank()) return
                    val pairs = data.split("@@SEP@@").mapNotNull { entry ->
                        val parts = entry.split("||", limit = 2)
                        if (parts.size != 2) return@mapNotNull null
                        val page = parts[1].toIntOrNull() ?: return@mapNotNull null
                        val title = parts[0].trim()
                        if (title.isBlank()) null else title to page
                    }
                    if (pairs.isNotEmpty()) onChaptersExtractedState?.invoke(pairs)
                }
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
    }, update = { wv ->
        if (html != lastLoadedHtml) {
            lastLoadedHtml = html
            wv.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        }
    }, modifier = modifier)
}

fun generateReaderCss(t: ReadingThemeType, fs: Float, lh: Float, pagedMode: Boolean = false): String {
    val (bg, fg, link) = when (t) { ReadingThemeType.LIGHT -> Triple("#FFF","#1A1A1A","#4A90D9"); ReadingThemeType.SEPIA -> Triple("#FBF0D9","#3E2723","#8D6E63"); ReadingThemeType.DARK -> Triple("#1A1A2E","#E8E8E8","#64B5F6") }
    val bodyRule = if (pagedMode) {
        "padding:12px 24px 8px 24px;margin:0;max-width:none;overflow:hidden;height:100vh;"
    } else {
        "padding:16px 24px;margin:0 auto;max-width:720px;"
    }
    val pagedReaderRule = if (pagedMode) {
        "#reader-content{height:100%;width:100%;column-width:calc(100vw - 48px);column-gap:24px;column-fill:auto;overflow:hidden;transition:transform 220ms cubic-bezier(0.22,0.61,0.36,1);}#reader-content>*{break-inside:avoid;-webkit-column-break-inside:avoid;page-break-inside:avoid;}#reader-content{overflow-wrap:anywhere;word-break:break-word;}#reader-content.turning-next{animation:contentTurnNext 320ms cubic-bezier(0.22,0.61,0.36,1);}#reader-content.turning-prev{animation:contentTurnPrev 320ms cubic-bezier(0.22,0.61,0.36,1);}#page-turn-overlay{position:fixed;top:0;bottom:0;width:52vw;pointer-events:none;opacity:0;z-index:999;transform-style:preserve-3d;backface-visibility:hidden;}#page-turn-overlay.active{opacity:1;}#page-turn-overlay.turn-next{right:0;transform-origin:right center;}#page-turn-overlay.turn-prev{left:0;transform-origin:left center;}#page-turn-overlay.turn-next.active{animation:pageCurlNext 320ms cubic-bezier(0.2,0.65,0.3,1) forwards;}#page-turn-overlay.turn-prev.active{animation:pageCurlPrev 320ms cubic-bezier(0.2,0.65,0.3,1) forwards;}@keyframes contentTurnNext{0%{transform:translateX(0);}40%{transform:translateX(-8px);}100%{transform:translateX(0);}}@keyframes contentTurnPrev{0%{transform:translateX(0);}40%{transform:translateX(8px);}100%{transform:translateX(0);}}@keyframes pageCurlNext{0%{transform:perspective(1200px) rotateY(0deg);background:linear-gradient(90deg,rgba(255,255,255,0.00) 5%,rgba(255,255,255,0.22) 32%,rgba(0,0,0,0.14) 78%,rgba(0,0,0,0.28) 100%);}60%{transform:perspective(1200px) rotateY(-24deg);background:linear-gradient(90deg,rgba(255,255,255,0.00) 2%,rgba(255,255,255,0.26) 24%,rgba(0,0,0,0.16) 70%,rgba(0,0,0,0.30) 100%);}100%{transform:perspective(1200px) rotateY(-52deg);background:linear-gradient(90deg,rgba(255,255,255,0.00) 0%,rgba(255,255,255,0.08) 14%,rgba(0,0,0,0.06) 62%,rgba(0,0,0,0.20) 100%);}}@keyframes pageCurlPrev{0%{transform:perspective(1200px) rotateY(0deg);background:linear-gradient(270deg,rgba(255,255,255,0.00) 5%,rgba(255,255,255,0.22) 32%,rgba(0,0,0,0.14) 78%,rgba(0,0,0,0.28) 100%);}60%{transform:perspective(1200px) rotateY(24deg);background:linear-gradient(270deg,rgba(255,255,255,0.00) 2%,rgba(255,255,255,0.26) 24%,rgba(0,0,0,0.16) 70%,rgba(0,0,0,0.30) 100%);}100%{transform:perspective(1200px) rotateY(52deg);background:linear-gradient(270deg,rgba(255,255,255,0.00) 0%,rgba(255,255,255,0.08) 14%,rgba(0,0,0,0.06) 62%,rgba(0,0,0,0.20) 100%);}}"
    } else {
        ""
    }
    return "* {box-sizing:border-box} body {background:$bg;color:$fg;font-family:-apple-system,sans-serif;font-size:${fs}px;line-height:$lh;$bodyRule word-wrap:break-word;} $pagedReaderRule a{color:$link} img{max-width:100%;height:auto} pre,code{background:${if(t==ReadingThemeType.DARK)"#0F3460" else "#F5F5F5"};padding:2px 6px;border-radius:4px;font-size:0.9em} blockquote{border-left:3px solid $link;margin-left:0;padding-left:16px} .translation{color:${if(t==ReadingThemeType.DARK)"#90A4AE" else "#666"};font-style:italic;background:${if(t==ReadingThemeType.DARK)"rgba(255,255,255,0.05)" else "rgba(0,0,0,0.03)"};border-left:3px solid $link;padding:8px 12px;margin:4px 0 16px 0}"
}
