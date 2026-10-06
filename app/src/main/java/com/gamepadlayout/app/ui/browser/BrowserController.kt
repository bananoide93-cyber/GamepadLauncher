package com.gamepadlayout.app.ui.browser

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import org.json.JSONObject
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

const val HOME_URL = "https://www.google.com"

fun normalizeUrl(input: String): String {
    val t = input.trim()
    if (t.isEmpty()) return HOME_URL
    if (t.startsWith("http://") || t.startsWith("https://")) return t
    val looksLikeHost = !t.contains(" ") && t.contains(".")
    return if (looksLikeHost) "https://$t" else "https://www.google.com/search?q=" + Uri.encode(t)
}

class BrowserTab(val id: Int, val webView: WebView) {
    var title by mutableStateOf("Nova aba")
    var url by mutableStateOf("")
    var progress by mutableIntStateOf(100)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)

    /** Sobe cada vez que o usuário foca um campo de texto dentro da página. */
    var editTick by mutableIntStateOf(0)
}

/** Gerencia abas (uma WebView por aba). As abas vivem enquanto o navegador estiver aberto. */
class BrowserController(private val context: Context, private val zoom: () -> Int) {
    val tabs = mutableStateListOf<BrowserTab>()
    var selected by mutableIntStateOf(0)
    private var nextId = 1

    val current: BrowserTab? get() = tabs.getOrNull(selected)

    @SuppressLint("SetJavaScriptEnabled")
    fun newTab(url: String? = HOME_URL) {
        val wv = WebView(context)
        val tab = BrowserTab(nextId++, wv)
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            textZoom = zoom()
        }
        fun sync(v: WebView?) {
            tab.canGoBack = v?.canGoBack() == true
            tab.canGoForward = v?.canGoForward() == true
            v?.url?.let { tab.url = it }
        }
        wv.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                url?.let { tab.url = it }
                sync(view)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                sync(view)
                view?.title?.takeIf { it.isNotBlank() }?.let { tab.title = it }
                view?.evaluateJavascript(EDIT_SCRIPT, null)
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                sync(view)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val scheme = request?.url?.scheme?.lowercase() ?: return false
                return scheme !in setOf("http", "https", "file", "about", "data", "javascript")
            }
        }
        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                tab.progress = newProgress
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                if (!title.isNullOrBlank()) tab.title = title
            }
        }
        wv.setDownloadListener { dlUrl, ua, cd, mime, _ -> download(dlUrl, ua, cd, mime) }
        wv.addJavascriptInterface(GlBridge { tab.editTick++ }, "GL")
        tabs.add(tab)
        selected = tabs.lastIndex
        wv.loadUrl(url ?: HOME_URL)
    }

    private fun download(url: String, ua: String?, cd: String?, mime: String?) {
        runCatching {
            val name = URLUtil.guessFileName(url, cd, mime)
            val req = DownloadManager.Request(Uri.parse(url))
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            if (!mime.isNullOrBlank()) req.setMimeType(mime)
            if (!ua.isNullOrBlank()) req.addRequestHeader("User-Agent", ua)
            CookieManager.getInstance().getCookie(url)?.let { req.addRequestHeader("Cookie", it) }
            (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
            Toast.makeText(context, "Baixando $name", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "Não foi possível iniciar o download", Toast.LENGTH_SHORT).show()
        }
    }

    fun next() { if (tabs.isNotEmpty()) selected = (selected + 1) % tabs.size }
    fun prev() { if (tabs.isNotEmpty()) selected = (selected - 1 + tabs.size) % tabs.size }

    fun setZoom(z: Int) { tabs.forEach { it.webView.settings.textZoom = z } }

    /** Retorna true se era a última aba (o chamador deve sair do navegador). */
    fun closeTab(index: Int): Boolean {
        if (index !in tabs.indices) return false
        if (tabs.size == 1) return true
        val t = tabs.removeAt(index)
        destroyTab(t)
        selected = if (index < selected) selected - 1 else minOf(selected, tabs.lastIndex)
        return false
    }

    private fun destroyTab(t: BrowserTab) {
        t.webView.apply {
            stopLoading()
            (parent as? ViewGroup)?.removeView(this)
            destroy()
        }
    }

    fun destroy() {
        tabs.toList().forEach { destroyTab(it) }
        tabs.clear()
    }
}

/** Ponte JS -> app: avisa quando um campo de texto da página ganha foco. */
class GlBridge(private val onEdit: () -> Unit) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onEditable(v: Boolean) {
        if (v) main.post { onEdit() }
    }
}

/** Detecta foco em campos de texto (input/textarea/contenteditable) e avisa o app. */
const val EDIT_SCRIPT = """(function(){if(window.__glInit)return;window.__glInit=true;
document.addEventListener('focusin',function(ev){var t=ev.target;if(!t)return;
var ed=t.isContentEditable||t.tagName==='TEXTAREA'||(t.tagName==='INPUT'&&!/^(button|submit|checkbox|radio|reset|file|image|range|color)$/i.test(t.type||'text'));
if(ed&&window.GL){GL.onEditable(true);}},true);})()"""

private const val TYPE_JS = """(function(t){var e=document.activeElement;if(!e)return;
if(e.isContentEditable){document.execCommand('insertText',false,t);return;}
if(e.tagName!=='INPUT'&&e.tagName!=='TEXTAREA')return;
var s=e.selectionStart==null?e.value.length:e.selectionStart;
var n=e.selectionEnd==null?s:e.selectionEnd;
var v=e.value.slice(0,s)+t+e.value.slice(n);
var d=Object.getOwnPropertyDescriptor(Object.getPrototypeOf(e),'value');
if(d&&d.set){d.set.call(e,v);}else{e.value=v;}
try{e.setSelectionRange(s+t.length,s+t.length);}catch(x){}
e.dispatchEvent(new Event('input',{bubbles:true}));})(__T__)"""

private const val BACKSPACE_JS = """(function(){var e=document.activeElement;if(!e)return;
if(e.isContentEditable){document.execCommand('delete');return;}
if(e.tagName!=='INPUT'&&e.tagName!=='TEXTAREA')return;
var s=e.selectionStart==null?e.value.length:e.selectionStart;
var n=e.selectionEnd==null?s:e.selectionEnd;
var a=s;if(s===n){if(s===0)return;a=s-1;}
var v=e.value.slice(0,a)+e.value.slice(n);
var d=Object.getOwnPropertyDescriptor(Object.getPrototypeOf(e),'value');
if(d&&d.set){d.set.call(e,v);}else{e.value=v;}
try{e.setSelectionRange(a,a);}catch(x){}
e.dispatchEvent(new Event('input',{bubbles:true}));})()"""

private const val ENTER_JS = """(function(){var e=document.activeElement;if(!e)return;
['keydown','keypress','keyup'].forEach(function(t){e.dispatchEvent(new KeyboardEvent(t,{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true}));});
if(e.form&&e.tagName==='INPUT'){if(e.form.requestSubmit){e.form.requestSubmit();}else{e.form.submit();}}})()"""

/** Digita texto no campo focado da página (usado pelo teclado do controle). */
fun WebView.pageType(text: String) {
    evaluateJavascript(TYPE_JS.replace("__T__", JSONObject.quote(text)), null)
}

fun WebView.pageBackspace() {
    evaluateJavascript(BACKSPACE_JS, null)
}

fun WebView.pageEnter() {
    evaluateJavascript(ENTER_JS, null)
}
