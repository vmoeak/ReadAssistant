package com.readassistant.feature.reader.presentation.renderer

import android.annotation.SuppressLint
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ActionMode
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
    extractCommandId: Int = 0,
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
        """<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1.0"><style>$css</style></head><body><div id="reader-content">$htmlContent</div><div id="page-turn-overlay"><div class="back"></div><div class="sheet"></div><div class="shade"></div><div class="gloss"></div><div class="crease"></div></div><script>
var pagedMode=$pagedFlag;
var pageIndex=0;
var totalPages=1;
var pageStride=0;
var isAnimating=false;
var dragActive=false;
var dragDirection=0;
var dragProgress=0;
function collectParagraphElements(){
  var base=document.getElementById('reader-content');
  if(!base) return [];
  var selector='#reader-content p,#reader-content h1,#reader-content h2,#reader-content h3,#reader-content h4,#reader-content li,#reader-content blockquote,#reader-content div';
  var blockContainerSelector='p,h1,h2,h3,h4,li,blockquote,div,table,ul,ol,pre';
  var nodes=Array.from(document.querySelectorAll(selector));
  return nodes.filter(function(el){
    if(!el || el.id==='reader-content') return false;
    if(el.classList && el.classList.contains('translation')) return false;
    var text=(el.textContent||'').trim();
    if(text.length===0) return false;
    if(el.tagName==='DIV'){
      if(el.querySelector(blockContainerSelector)) return false;
    }
    return true;
  });
}
var paras=collectParagraphElements();
paras.forEach(function(p,i){p.setAttribute('data-para-idx',i)});
document.addEventListener('contextmenu',function(e){e.preventDefault();});
document.addEventListener('selectionchange',function(){var s=window.getSelection();if(s&&s.toString().trim().length>0){try{var r=s.getRangeAt(0);var rect=r.getBoundingClientRect();var p=r.startContainer.parentElement;while(p&&!p.getAttribute('data-para-idx'))p=p.parentElement;Android.onTextSelected(s.toString(),r.startOffset,r.endOffset,p?parseInt(p.getAttribute('data-para-idx')):-1,rect.left,rect.top,rect.right,rect.bottom)}catch(e){}}});
var st;window.addEventListener('scroll',function(){clearTimeout(st);st=setTimeout(function(){var t=window.pageYOffset;var h=document.documentElement.scrollHeight-window.innerHeight;Android.onScrollProgress(h>0?t/h:0)},200)});
function extractParagraphs(){
  var r=[];
  var pageStart=pageIndex*pageStride;
  var pageEnd=pageStart+pageStride;
  paras.forEach(function(p,i){
    var t=p.textContent.trim();
    if(t.length===0) return;
    if(pagedMode){
      var rect=p.getBoundingClientRect();
      var left=p.offsetLeft||0;
      var right=left+Math.max(1,p.offsetWidth||1);
      var inCurrentPage=(left<pageEnd && right>pageStart);
      var visible=rect.bottom>0 && rect.top<window.innerHeight && rect.right>0 && rect.left<window.innerWidth;
      var keep=inCurrentPage||visible;
      if(!keep) return;
    }
    r.push(i+'||'+t);
  });
  if(r.length===0){
    paras.forEach(function(p,i){
      var t=p.textContent.trim();
      if(t.length>0) r.push(i+'||'+t);
    });
  }
  Android.onParagraphsExtracted(r.join('@@SEP@@'));
}
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
function clamp(v,min,max){return Math.max(min,Math.min(max,v));}
function setDragVisual(progress){
  var overlay=document.getElementById('page-turn-overlay');
  var content=document.getElementById('reader-content');
  if(!overlay || !content) return;
  var back=overlay.querySelector('.back');
  var sheet=overlay.querySelector('.sheet');
  var shade=overlay.querySelector('.shade');
  var gloss=overlay.querySelector('.gloss');
  var crease=overlay.querySelector('.crease');
  if(!sheet || !shade || !gloss || !back || !crease) return;
  var p=Math.max(0,Math.min(1,progress));
  var eased=1-Math.pow(1-p,1.55);
  var vw=Math.max(1,window.innerWidth);
  var rotate=6+36*eased;
  var skew=0.4+1.4*eased;
  var shift=2+4*eased;
  var shadowAlpha=(0.28+0.44*p).toFixed(3);
  sheet.style.opacity='0.92';
  if(dragDirection>0){
    var topX=clamp(vw*(1-p),0,vw);
    var botX=clamp(vw*(1-0.68*p),0,vw);
    var sw=vw*0.13;
    var sTopL=clamp(topX-sw,0,vw);
    var sBotL=clamp(botX-sw*0.9,0,vw);
    sheet.style.transformOrigin='right center';
    sheet.style.transform='rotateY('+(-rotate)+'deg) skewY('+(-skew)+'deg)';
    sheet.style.clipPath='polygon('+topX+'px 0,100% 0,100% 100%,'+botX+'px 100%)';
    back.style.opacity=String((0.10+0.22*eased).toFixed(3));
    back.style.clipPath='polygon(0 0,'+sTopL+'px 0,'+sBotL+'px 100%,0 100%)';
    var sTopLpct=(sTopL/vw*100).toFixed(1);
    var topXpct=(topX/vw*100).toFixed(1);
    shade.style.opacity='1';
    shade.style.clipPath='polygon('+sTopL+'px 0,'+topX+'px 0,'+botX+'px 100%,'+sBotL+'px 100%)';
    shade.style.background='linear-gradient(90deg,rgba(0,0,0,0) '+sTopLpct+'%,rgba(0,0,0,'+shadowAlpha+') '+topXpct+'%)';
    gloss.style.opacity=String((0.06+0.28*eased).toFixed(3));
    gloss.style.background='linear-gradient(270deg,rgba(255,255,255,0.55) 0%,rgba(255,255,255,0.02) 100%)';
    crease.style.left=(topX-2)+'px';
    crease.style.right='';
    crease.style.transform='rotate('+(-6*p)+'deg)';
    crease.style.opacity=String((0.55+0.40*eased).toFixed(3));
    content.style.transform='translateX('+(-shift)+'px)';
  }else{
    var topX2=clamp(vw*p,0,vw);
    var botX2=clamp(vw*0.68*p,0,vw);
    var sw2=vw*0.13;
    var sTopR=clamp(topX2+sw2,0,vw);
    var sBotR=clamp(botX2+sw2*0.9,0,vw);
    sheet.style.transformOrigin='left center';
    sheet.style.transform='rotateY('+rotate+'deg) skewY('+skew+'deg)';
    sheet.style.clipPath='polygon(0 0,'+topX2+'px 0,'+botX2+'px 100%,0 100%)';
    back.style.opacity=String((0.10+0.22*eased).toFixed(3));
    back.style.clipPath='polygon('+sTopR+'px 0,100% 0,100% 100%,'+sBotR+'px 100%)';
    var topX2pct=(topX2/vw*100).toFixed(1);
    var sTopRpct=(sTopR/vw*100).toFixed(1);
    shade.style.opacity='1';
    shade.style.clipPath='polygon('+topX2+'px 0,'+sTopR+'px 0,'+sBotR+'px 100%,'+botX2+'px 100%)';
    shade.style.background='linear-gradient(90deg,rgba(0,0,0,'+shadowAlpha+') '+topX2pct+'%,rgba(0,0,0,0) '+sTopRpct+'%)';
    gloss.style.opacity=String((0.06+0.28*eased).toFixed(3));
    gloss.style.background='linear-gradient(90deg,rgba(255,255,255,0.55) 0%,rgba(255,255,255,0.02) 100%)';
    crease.style.right=(vw-topX2-2)+'px';
    crease.style.left='';
    crease.style.transform='rotate('+6*p+'deg)';
    crease.style.opacity=String((0.55+0.40*eased).toFixed(3));
    content.style.transform='translateX('+shift+'px)';
  }
  overlay.style.opacity='1';
}
function clearDragVisual(){
  var overlay=document.getElementById('page-turn-overlay');
  var content=document.getElementById('reader-content');
  var body=document.body;
  if(content) content.style.transform='';
  if(body) body.classList.remove('dragging');
  if(!overlay) return;
  var back=overlay.querySelector('.back');
  var sheet=overlay.querySelector('.sheet');
  var shade=overlay.querySelector('.shade');
  var gloss=overlay.querySelector('.gloss');
  var crease=overlay.querySelector('.crease');
  if(back){back.style.clipPath='';back.style.opacity='';}
  if(sheet){sheet.style.transform='';sheet.style.clipPath='';}
  if(shade){shade.style.opacity='';shade.style.background='';}
  if(gloss){gloss.style.opacity='';gloss.style.background='';}
  if(crease){crease.style.opacity='';crease.style.left='';crease.style.right='';crease.style.transform='';}
  overlay.style.opacity='';
  overlay.classList.remove('active','turn-next','turn-prev');
}
function animateDragTo(target,duration,done){
  var start=dragProgress;
  var from=performance.now();
  function tick(now){
    var t=Math.min(1,(now-from)/duration);
    var e=t<0.5?2*t*t:1-Math.pow(-2*t+2,2)/2;
    dragProgress=start+(target-start)*e;
    setDragVisual(dragProgress);
    if(t<1){requestAnimationFrame(tick);}else{done();}
  }
  requestAnimationFrame(tick);
}
function beginDrag(direction){
  if(!pagedMode || isAnimating || dragActive) return false;
  if(direction>0 && pageIndex>=totalPages-1) return false;
  if(direction<0 && pageIndex<=0) return false;
  var overlay=document.getElementById('page-turn-overlay');
  var body=document.body;
  if(!overlay) return false;
  dragDirection=direction>0?1:-1;
  dragProgress=0.02;
  dragActive=true;
  overlay.classList.remove('turn-next','turn-prev');
  overlay.classList.add('active',dragDirection>0?'turn-next':'turn-prev');
  if(body) body.classList.add('dragging');
  setDragVisual(dragProgress);
  return true;
}
function updateDragProgress(value){
  if(!dragActive) return false;
  dragProgress=Math.max(0.02,Math.min(1,parseFloat(value)||0));
  setDragVisual(dragProgress);
  return true;
}
function endDrag(commit){
  if(!dragActive) return false;
  var shouldCommit=(commit===true||commit==='true') && dragProgress>0.06;
  if(shouldCommit){
    animateDragTo(1,180,function(){
      if(dragDirection>0) pageIndex+=1; else pageIndex-=1;
      dragActive=false;
      dragDirection=0;
      dragProgress=0;
      clearDragVisual();
      updatePageMetrics();
    });
  }else{
    animateDragTo(0,160,function(){
      dragActive=false;
      dragDirection=0;
      dragProgress=0;
      clearDragVisual();
    });
  }
  return true;
}
function runTurnAnimation(direction, onMidpoint){
  if(!pagedMode || isAnimating || dragActive) return false;
  if(direction>0 && pageIndex>=totalPages-1){onMidpoint();return true;}
  if(direction<0 && pageIndex<=0){onMidpoint();return true;}
  var overlay=document.getElementById('page-turn-overlay');
  var body=document.body;
  isAnimating=true;
  dragDirection=direction>0?1:-1;
  dragProgress=0;
  dragActive=true;
  if(overlay){
    overlay.classList.remove('turn-next','turn-prev');
    overlay.classList.add('active',dragDirection>0?'turn-next':'turn-prev');
  }
  if(body) body.classList.add('dragging');
  var duration=380;
  var midFired=false;
  var from=performance.now();
  function tick(now){
    var t=Math.min(1,(now-from)/duration);
    var e=t<0.5?2*t*t:1-Math.pow(-2*t+2,2)/2;
    dragProgress=e;
    setDragVisual(dragProgress);
    if(!midFired && t>=0.52){midFired=true;onMidpoint();}
    if(t<1){
      requestAnimationFrame(tick);
    }else{
      if(!midFired){midFired=true;onMidpoint();}
      dragActive=false;
      dragDirection=0;
      dragProgress=0;
      isAnimating=false;
      clearDragVisual();
    }
  }
  requestAnimationFrame(tick);
  return true;
}
function nextPage(){
  if(!pagedMode) return false;
  if(pageIndex>=totalPages-1) return false;
  pageIndex+=1;
  updatePageMetrics();
  return true;
}
function prevPage(){
  if(!pagedMode) return false;
  if(pageIndex<=0) return false;
  pageIndex-=1;
  updatePageMetrics();
  return true;
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
function clearNativeSelection(){
  try{
    var s=window.getSelection();
    if(s) s.removeAllRanges();
    var ae=document.activeElement;
    if(ae && ae.blur) ae.blur();
  }catch(e){}
}
</script></body></html>"""
    }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val onSwipeLeftState by rememberUpdatedState(onSwipeLeft)
    val onSwipeRightState by rememberUpdatedState(onSwipeRight)
    val onPageChangedState by rememberUpdatedState(onPageChanged)
    val onChaptersExtractedState by rememberUpdatedState(onChaptersExtracted)
    val onSingleTapState by rememberUpdatedState(onSingleTap)
    val isBilingualModeState by rememberUpdatedState(isBilingualMode)
    var lastHandledSeekCommandId by remember { mutableStateOf(0) }
    var lastHandledExtractCommandId by remember { mutableStateOf(0) }

    // When bilingual mode is toggled on, extract paragraphs; when off, remove translations
    LaunchedEffect(isBilingualMode) {
        val wv = webViewRef ?: return@LaunchedEffect
        if (isBilingualMode) {
            wv.evaluateJavascript("extractParagraphs()", null)
            // Re-apply already fetched translations when user toggles bilingual mode back on.
            translations.forEach { (idx, pair) ->
                val escaped = pair.translatedText
                    .replace("\\", "\\\\")
                    .replace("'", "\\'")
                    .replace("\n", "\\n")
                wv.evaluateJavascript("setTranslation($idx,'$escaped')", null)
            }
        } else {
            wv.evaluateJavascript("removeAllTranslations()", null)
        }
    }
    LaunchedEffect(extractCommandId) {
        val wv = webViewRef ?: return@LaunchedEffect
        if (extractCommandId == 0 || extractCommandId == lastHandledExtractCommandId) return@LaunchedEffect
        if (isBilingualMode) wv.evaluateJavascript("extractParagraphs()", null)
        lastHandledExtractCommandId = extractCommandId
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
        object : WebView(ctx) {
            override fun startActionMode(callback: ActionMode.Callback?): ActionMode? = null
            override fun startActionMode(callback: ActionMode.Callback?, type: Int): ActionMode? = null
        }.apply {
            settings.javaScriptEnabled = true; settings.domStorageEnabled = true
            val gestureDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    evaluateJavascript("clearNativeSelection()", null)
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
                false
            }
            addJavascriptInterface(object {
                @JavascriptInterface fun onTextSelected(text: String, s: Int, e: Int, p: Int, l: Float, t: Float, r: Float, b: Float) { onTextSelected(TextSelection(text, s, e, p, SelectionRect(l, t, r, b))) }
                @JavascriptInterface fun onScrollProgress(p: Float) { onProgressChanged(p) }
                @JavascriptInterface fun onPageChanged(currentPage: Int, total: Int, progress: Float) {
                    onPageChangedState?.invoke(currentPage, total, progress)
                    if (isBilingualModeState) {
                        webViewRef?.post {
                            webViewRef?.evaluateJavascript("extractParagraphs()", null)
                        }
                    }
                }
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
                    android.util.Log.w(
                        "ReadAssistant",
                        "onParagraphsExtracted size=${pairs.size} sampleIdx=${pairs.firstOrNull()?.first ?: -1}"
                    )
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
    val curlHighlight = if (t == ReadingThemeType.DARK) "rgba(255,255,255,0.24)" else "rgba(255,255,255,0.82)"
    val curlShadowStrong = if (t == ReadingThemeType.DARK) "rgba(0,0,0,0.58)" else "rgba(0,0,0,0.28)"
    val curlShadowSoft = if (t == ReadingThemeType.DARK) "rgba(0,0,0,0.34)" else "rgba(0,0,0,0.16)"
    // Opaque fold crease colors (no transparency — sheet must be fully opaque so text doesn't bleed through)
    val foldShadow = when (t) { ReadingThemeType.DARK -> "#0e0e22"; ReadingThemeType.SEPIA -> "#c8bba5"; else -> "#d0d0d0" }
    val foldHighlight = when (t) { ReadingThemeType.DARK -> "#28284a"; ReadingThemeType.SEPIA -> "#fdf4e4"; else -> "#ffffff" }
    val bodyRule = if (pagedMode) {
        "padding:12px 24px 8px 24px;margin:0;max-width:none;overflow:hidden;height:100vh;"
    } else {
        "padding:16px 24px;margin:0 auto;max-width:720px;"
    }
    val pagedReaderRule = if (pagedMode) {
        """
        #reader-content{height:100%;width:100%;column-width:calc(100vw - 48px);column-gap:24px;column-fill:auto;overflow:hidden;overflow-wrap:anywhere;word-break:break-word;transform-origin:center center;will-change:transform;backface-visibility:hidden;}
        #reader-content>*{break-inside:avoid;-webkit-column-break-inside:avoid;page-break-inside:avoid;}
        body.dragging,body.dragging *{-webkit-user-select:none !important;user-select:none !important;-webkit-touch-callout:none !important;}
        #page-turn-overlay{position:fixed;inset:0;pointer-events:none;opacity:0;z-index:999;transform-style:preserve-3d;backface-visibility:hidden;perspective:2200px;overflow:hidden;will-change:opacity;}
        #page-turn-overlay.active{opacity:1;}
        #page-turn-overlay.turn-next{transform-origin:right center;}
        #page-turn-overlay.turn-prev{transform-origin:left center;}
        #page-turn-overlay .back,#page-turn-overlay .sheet,#page-turn-overlay .shade,#page-turn-overlay .gloss{
          position:absolute;inset:0;opacity:0;backface-visibility:hidden;will-change:transform,clip-path,opacity;
        }
        #page-turn-overlay .sheet{
          background:$bg;opacity:0.88;
        }
        #page-turn-overlay.turn-next .sheet{
          background:linear-gradient(90deg,$foldShadow 0%,$foldHighlight 6%,$bg 18%);
        }
        #page-turn-overlay.turn-prev .sheet{
          background:linear-gradient(270deg,$foldShadow 0%,$foldHighlight 6%,$bg 18%);
        }
        #page-turn-overlay .back{
          background:linear-gradient(90deg,rgba(0,0,0,0.05) 0%,rgba(255,255,255,0.24) 52%,rgba(0,0,0,0.14) 100%);
          mix-blend-mode:multiply;
        }
        #page-turn-overlay.turn-prev .back{
          background:linear-gradient(270deg,rgba(0,0,0,0.05) 0%,rgba(255,255,255,0.24) 52%,rgba(0,0,0,0.14) 100%);
        }
        #page-turn-overlay .shade{opacity:0;}
        #page-turn-overlay .gloss{opacity:0;mix-blend-mode:screen;}
        #page-turn-overlay .crease{
          position:absolute;top:0;bottom:0;width:5px;opacity:0;
          background:linear-gradient(90deg,rgba(0,0,0,0.18) 0%,rgba(255,255,255,0.92) 40%,rgba(255,255,255,0.60) 100%);
          filter:blur(1.5px);
          will-change:left,right,opacity,transform;
        }
        """
    } else {
        ""
    }
    return "* {box-sizing:border-box} body {background:$bg;color:$fg;font-family:-apple-system,sans-serif;font-size:${fs}px;line-height:$lh;$bodyRule word-wrap:break-word;} $pagedReaderRule a{color:$link} img{max-width:100%;height:auto} pre,code{background:${if(t==ReadingThemeType.DARK)"#0F3460" else "#F5F5F5"};padding:2px 6px;border-radius:4px;font-size:0.9em} blockquote{border-left:3px solid $link;margin-left:0;padding-left:16px} .translation{color:${if(t==ReadingThemeType.DARK)"#90A4AE" else "#666"};font-style:italic;background:${if(t==ReadingThemeType.DARK)"rgba(255,255,255,0.05)" else "rgba(0,0,0,0.03)"};border-left:3px solid $link;padding:8px 12px;margin:4px 0 16px 0}"
}
