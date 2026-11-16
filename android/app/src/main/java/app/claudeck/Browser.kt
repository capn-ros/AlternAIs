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
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import app.claudeck.ui.browser.BrowserActions
import app.claudeck.ui.browser.BrowserState
import app.claudeck.ui.browser.createBrowserBar
import app.claudeck.ui.common.TabUi

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

/** Una pestaña del navegador: su página (WebView) y lo último que se supo de ella. */
class BrowserTab(val id: Int, val webView: WebView) {
    var title: String = ""
    var url: String = ""
    var loading = false
    var progress = 100
    var started = false
}

/** Todas las pestañas abiertas; sobrevive al rearmado del cajón (cambio de tema) sin perder ninguna página. */
class BrowserSession {
    val tabs = mutableListOf<BrowserTab>()
    var activeId = 0
    var nextId = 1

    val activeWebView: WebView? get() = tabs.firstOrNull { it.id == activeId }?.webView ?: tabs.firstOrNull()?.webView
}

/**
 * Navegador web interno, universal para toda la app (no pertenece a ninguna cuenta): un cajón casi a pantalla
 * completa con pestañas, atrás, adelante, recargar, barra de direcciones y accesos rápidos. Tiene su propia sesión
 * (perfil «browser»), compartida por todas las pestañas, así que si entrás a GitHub u otro sitio se queda iniciada.
 */
class BrowserPanel(private val act: AppCompatActivity, private val host: BrowserHost, existing: BrowserSession?) {
    val view: LinearLayout = LinearLayout(act)
    val session: BrowserSession = existing ?: BrowserSession()

    /** La página de la pestaña activa. */
    val webView: WebView get() = active().webView

    private val pages = FrameLayout(act)

    /** Deslizar hacia abajo desde arriba de la página recarga la pestaña activa (como en Chrome). */
    private val swipe = androidx.swiperefreshlayout.widget.SwipeRefreshLayout(act)
    private val state = BrowserState()

    private fun active(): BrowserTab = session.tabs.first { it.id == session.activeId }
    private fun tabOf(wv: WebView?): BrowserTab? = session.tabs.firstOrNull { it.webView === wv }

    private val actions = object : BrowserActions {
        override fun back() { if (webView.canGoBack()) webView.goBack() }
        override fun forward() { if (webView.canGoForward()) webView.goForward() }
        override fun reloadOrStop() { if (active().loading) webView.stopLoading() else webView.reload() }
        override fun go(raw: String) = this@BrowserPanel.go(raw)
        override fun close() = host.close()
        override fun downloadSettings() = host.downloadSettings()
        override fun openExternal() = this@BrowserPanel.openExternal()
        override fun selectTab(id: Int) = this@BrowserPanel.selectTab(id)
        override fun newTab() { this@BrowserPanel.newTab() }
        override fun closeTab(id: Int) = this@BrowserPanel.closeTab(id)
    }

    init {
        if (session.tabs.isEmpty()) {
            val t = createTab()
            session.activeId = t.id
        }
        for (t in session.tabs) wire(t) // si el cajón se rearmó, los callbacks tienen que apuntar a este panel
        build()
        publishTabs()
        applyActive()
    }

    /** Cambia el color del borde del cajón (sigue a la cuenta activa). */
    fun setBorder(color: Int) {
        (view.background as? GradientDrawable)?.setStroke(dp(3), color)
    }

    private fun dp(v: Int): Int = (v * act.resources.displayMetrics.density).toInt()

    // ---------------------------------------------------------------- Pestañas

    private fun createTab(): BrowserTab {
        val t = BrowserTab(session.nextId++, createWebView())
        session.tabs += t
        wire(t)
        return t
    }

    private fun tabLabel(t: BrowserTab): String = when {
        t.title.isNotBlank() -> t.title
        t.url.isNotBlank() -> Uri.parse(t.url).host?.removePrefix("www.") ?: t.url
        else -> "Pestaña nueva"
    }

    private fun publishTabs() {
        state.tabs = session.tabs.map { TabUi(it.id, tabLabel(it)) }
        state.activeTab = session.activeId
    }

    /** Pasa a la barra lo de la pestaña activa y deja visible solo su página. */
    private fun applyActive() {
        val t = active()
        for (o in session.tabs) o.webView.visibility = if (o === t) View.VISIBLE else View.GONE
        state.url = t.url
        state.progress = t.progress
        state.loading = t.loading
        state.clearFocusTick++
        swipe.isRefreshing = false
        syncUi()
    }

    private fun selectTab(id: Int) {
        if (id == session.activeId || session.tabs.none { it.id == id }) return
        session.activeId = id
        publishTabs()
        applyActive()
        if (!active().started) startTab(active(), null)
    }

    /** Abre una pestaña nueva (con la página de inicio) y pasa a ella. */
    fun newTab(): BrowserTab {
        val t = createTab()
        pages.addView(t.webView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        session.activeId = t.id
        publishTabs()
        applyActive()
        startTab(t, null)
        return t
    }

    private fun closeTab(id: Int) {
        if (session.tabs.size <= 1) return
        val i = session.tabs.indexOfFirst { it.id == id }
        if (i < 0) return
        val t = session.tabs.removeAt(i)
        if (session.activeId == id) session.activeId = session.tabs[minOf(i, session.tabs.size - 1)].id
        pages.removeView(t.webView)
        t.webView.stopLoading()
        t.webView.destroy()
        publishTabs()
        applyActive()
        if (!active().started) startTab(active(), null)
    }

    private fun startTab(t: BrowserTab, lastUrl: String?) {
        t.started = true
        navigate(t, BrowserRules.startUrl(lastUrl))
    }

    fun resumeAll() { session.tabs.forEach { it.webView.onResume() } }
    fun pauseAll() { session.tabs.forEach { it.webView.onPause() } }

    fun destroyAll() {
        for (t in session.tabs) {
            (t.webView.parent as? ViewGroup)?.removeView(t.webView)
            t.webView.destroy()
        }
        session.tabs.clear()
    }

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
        return wv
    }

    /** Conecta los callbacks de la página de la pestaña [t] con este panel. */
    private fun wire(t: BrowserTab) {
        val wv = t.webView
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
                t.loading = true
                t.url = url ?: ""
                changed(t)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                t.loading = false
                t.url = url ?: ""
                remember(t, url)
                changed(t)
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                t.url = url ?: ""
                remember(t, url)
                changed(t)
            }
        }
        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                t.progress = newProgress
                if (t.id == session.activeId) state.progress = newProgress
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                t.title = title ?: ""
                publishTabs()
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
    }

    /** Algo cambió en la página de [t]: si es la que se está viendo, se actualiza la barra. */
    private fun changed(t: BrowserTab) {
        if (t.id != session.activeId) return
        if (!state.urlFocused) state.url = t.url
        if (!t.loading) swipe.isRefreshing = false
        syncUi()
        publishTabs()
    }

    /** Para rearmar el cajón (cambio de tema) sin perder las páginas abiertas. */
    fun detachSession(): BrowserSession {
        for (t in session.tabs) (t.webView.parent as? ViewGroup)?.removeView(t.webView)
        return session
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

        // Barra (Compose): pestañas · atrás · adelante · recargar · dirección · cerrar, progreso y accesos rápidos.
        view.addView(
            createBrowserBar(act, state, actions, host.surface(), host.onSurface(), host.accent()),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        // Todas las páginas conviven en el mismo marco; solo la de la pestaña activa se ve.
        for (t in session.tabs) {
            (t.webView.parent as? ViewGroup)?.removeView(t.webView)
            pages.addView(t.webView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        swipe.addView(pages, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        // Solo se refresca si la página está arriba del todo (si no, el gesto es para desplazarla).
        swipe.setOnChildScrollUpCallback { _, _ -> webView.scrollY > 0 }
        swipe.setOnRefreshListener { webView.reload() }
        swipe.setProgressBackgroundColorSchemeColor(host.surface())
        swipe.setColorSchemeColors(host.accent())
        view.addView(swipe, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    /** Guarda la última página para retomarla la próxima vez (solo http y https, de la pestaña que se ve). */
    private fun remember(t: BrowserTab, url: String?) {
        if (t.id == session.activeId && url != null && BrowserRules.isRememberable(url)) host.saveLastUrl(url)
    }

    /** Pasa a la barra el estado del historial y si está cargando (cambia recargar por «detener»). */
    private fun syncUi() {
        val t = active()
        state.canGoBack = t.webView.canGoBack()
        state.canGoForward = t.webView.canGoForward()
        state.loading = t.loading
    }

    // ---------------------------------------------------------------- Navegación

    /** Al abrir el cajón: la primera vez carga la última página; las siguientes conserva las pestañas como estaban. */
    fun open() {
        swipe.setColorSchemeColors(host.accent()) // del color de la cuenta activa
        val t = active()
        if (!t.started) startTab(t, host.lastUrl())
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
        navigate(active(), url)
    }

    private fun navigate(t: BrowserTab, url: String) {
        t.started = true
        if (t.id == session.activeId) {
            state.clearFocusTick++
            t.webView.requestFocus()
        }
        t.webView.loadUrl(url)
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
