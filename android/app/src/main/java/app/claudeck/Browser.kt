package app.claudeck

import android.annotation.SuppressLint
import app.claudeck.domain.usecase.BrowserBack
import app.claudeck.domain.usecase.BrowserInput
import app.claudeck.domain.usecase.BrowserRules
import app.claudeck.domain.usecase.LinkAction
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import app.claudeck.ui.browser.BrowserActions
import app.claudeck.ui.browser.BrowserState
import app.claudeck.ui.browser.createBrowserBar

/** Lo que el navegador interno le pide a la pantalla principal. */
interface BrowserHost {
    fun surface(): Int
    fun onSurface(): Int
    fun accent(): Int
    fun border(): Int
    fun close()
    fun message(text: String)
    fun multiProfile(): Boolean
    fun chooseFile(callback: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams): Boolean
    fun download(wv: WebView, url: String, ua: String?, cd: String?, mime: String?)
    fun lastUrl(): String?
    fun saveLastUrl(url: String)
    fun downloadSettings()
}

/**
 * Navegador web interno, universal para toda la app (no pertenece a ninguna cuenta): un cajón casi a pantalla
 * completa con atrás, adelante, recargar, barra de direcciones y accesos rápidos. Tiene su propia sesión
 * (perfil «browser»), así que si entrás a GitHub u otro sitio se queda iniciada.
 */
class BrowserPanel(private val act: AppCompatActivity, private val host: BrowserHost, existing: WebView?) {
    val view: LinearLayout = LinearLayout(act)
    val webView: WebView = existing ?: createWebView()

    private val state = BrowserState()
    private var loading = false
    private var started = existing != null

    private val actions = object : BrowserActions {
        override fun back() { if (webView.canGoBack()) webView.goBack() }
        override fun forward() { if (webView.canGoForward()) webView.goForward() }
        override fun reloadOrStop() { if (loading) webView.stopLoading() else webView.reload() }
        override fun go(raw: String) = this@BrowserPanel.go(raw)
        override fun close() = host.close()
        override fun downloadSettings() = host.downloadSettings()
        override fun openExternal() = this@BrowserPanel.openExternal()
    }

    init {
        build()
        if (existing != null) syncUi()
    }

    /** Cambia el color del borde del cajón (sigue a la cuenta activa). */
    fun setBorder(color: Int) {
        (view.background as? GradientDrawable)?.setStroke(dp(3), color)
    }

    private fun dp(v: Int): Int = (v * act.resources.displayMetrics.density).toInt()

    // ---------------------------------------------------------------- WebView

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView {
        val wv = WebView(act)
        if (host.multiProfile()) {
            try {
                ProfileStore.getInstance().getOrCreateProfile(PROFILE)
                WebViewCompat.setProfile(wv, PROFILE)
            } catch (_: Exception) {
            }
        }
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            setSupportMultipleWindows(false)
            mediaPlaybackRequiresUserGesture = true
            builtInZoomControls = true
            displayZoomControls = false
            // Sin «; wv» para que sitios como GitHub no lo traten como un navegador incrustado.
            userAgentString = BrowserRules.cleanUserAgent(userAgentString)
        }
        try {
            if (host.multiProfile()) {
                ProfileStore.getInstance().getProfile(PROFILE)?.cookieManager?.setAcceptThirdPartyCookies(wv, true)
            } else {
                CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
            }
        } catch (_: Exception) {
        }
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                return when (BrowserRules.linkAction(uri.scheme)) {
                    LinkAction.LOAD_HERE -> false
                    LinkAction.OPEN_EXTERNAL -> {
                        try {
                            act.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        } catch (_: Exception) {
                        }
                        true
                    }
                    LinkAction.BLOCK -> true // intent:, market:, etc. no se abren desde acá
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                loading = true
                if (!state.urlFocused) state.url = url ?: ""
                syncUi()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                loading = false
                remember(url)
                if (!state.urlFocused) state.url = url ?: ""
                syncUi()
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                remember(url)
                if (!state.urlFocused) state.url = url ?: ""
                syncUi()
            }
        }
        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                state.progress = newProgress
            }

            override fun onShowFileChooser(
                webView: WebView?, filePathCallback: ValueCallback<Array<Uri>>?, fileChooserParams: FileChooserParams?
            ): Boolean {
                if (filePathCallback == null || fileChooserParams == null) return false
                return host.chooseFile(filePathCallback, fileChooserParams)
            }
        }
        wv.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            host.download(wv, url, userAgent, contentDisposition, mimeType)
        }
        return wv
    }

    /** Para rearmar el cajón (cambio de tema) sin perder la página abierta. */
    fun detachWebView(): WebView {
        (webView.parent as? ViewGroup)?.removeView(webView)
        return webView
    }

    // ---------------------------------------------------------------- Interfaz

    private fun build() {
        val r = dp(22).toFloat()
        view.orientation = LinearLayout.VERTICAL
        view.elevation = dp(8).toFloat()
        view.isClickable = true
        view.visibility = View.GONE
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            setColor(host.surface())
            setStroke(dp(3), host.border())
        }
        view.clipToOutline = true
        view.setPadding(dp(3), dp(10), dp(3), 0) // deja a la vista el borde del cajón a los costados

        // Barra (Compose): atrás · adelante · recargar · dirección · cerrar, progreso y accesos rápidos.
        view.addView(
            createBrowserBar(act, state, actions, host.surface(), host.onSurface(), host.accent()),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        (webView.parent as? ViewGroup)?.removeView(webView)
        view.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    /** Guarda la última página para retomarla la próxima vez (solo http y https). */
    private fun remember(url: String?) {
        if (url != null && BrowserRules.isRememberable(url)) host.saveLastUrl(url)
    }

    /** Pasa a la barra el estado del historial y si está cargando (cambia recargar por «detener»). */
    private fun syncUi() {
        state.canGoBack = webView.canGoBack()
        state.canGoForward = webView.canGoForward()
        state.loading = loading
    }

    // ---------------------------------------------------------------- Navegación

    /** Al abrir el cajón: la primera vez carga GitHub; las siguientes conserva la página. */
    fun open() {
        if (!started) {
            started = true
            go(BrowserRules.startUrl(host.lastUrl()))
        }
        syncUi()
    }

    /** Atrás del navegador; devuelve false si no hay a dónde volver (entonces se cierra el cajón). */
    fun onBack(): Boolean = when (BrowserRules.back(state.urlFocused, webView.canGoBack())) {
        BrowserBack.LEAVE_FIELD -> {
            state.clearFocusTick++
            true
        }
        BrowserBack.GO_BACK -> {
            webView.goBack()
            true
        }
        BrowserBack.CLOSE -> false
    }

    /** Dirección → https; texto suelto → búsqueda en DuckDuckGo. Solo http y https. */
    private fun go(raw: String) {
        val url = when (val input = BrowserRules.resolve(raw) { Uri.encode(it) }) {
            is BrowserInput.Ignore -> return
            is BrowserInput.Reject -> {
                host.message(input.message)
                return
            }
            is BrowserInput.Load -> input.url
        }
        state.clearFocusTick++
        webView.requestFocus()
        webView.loadUrl(url)
    }

    private fun openExternal() {
        val u = webView.url ?: return
        try {
            act.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
        } catch (_: Exception) {
            host.message("No se pudo abrir el navegador del sistema")
        }
    }

    companion object {
        const val PROFILE = BrowserRules.PROFILE
        val QUICK = BrowserRules.QUICK_LINKS
    }
}
