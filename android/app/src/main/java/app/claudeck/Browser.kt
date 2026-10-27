package app.claudeck

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.ColorUtils
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import com.google.android.material.progressindicator.LinearProgressIndicator

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
}

/**
 * Navegador web interno, universal para toda la app (no pertenece a ninguna cuenta): un cajón casi a pantalla
 * completa con atrás, adelante, recargar, barra de direcciones y accesos rápidos. Tiene su propia sesión
 * (perfil «browser»), así que si entrás a GitHub u otro sitio se queda iniciada.
 */
class BrowserPanel(private val act: AppCompatActivity, private val host: BrowserHost, existing: WebView?) {
    val view: LinearLayout = LinearLayout(act)
    val webView: WebView = existing ?: createWebView()

    private val onSurface = host.onSurface()
    private val muted = ColorUtils.setAlphaComponent(host.onSurface(), 150)
    private val accent = host.accent()
    private lateinit var urlField: EditText
    private lateinit var progress: LinearProgressIndicator
    private lateinit var backBtn: ImageButton
    private lateinit var fwdBtn: ImageButton
    private lateinit var reloadBtn: ImageButton
    private var loading = false
    private var started = existing != null

    init {
        build()
        if (existing != null) syncUi()
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
            userAgentString = userAgentString.replace("; wv", "")
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
                val scheme = uri.scheme?.lowercase() ?: return false
                if (scheme == "http" || scheme == "https") return false
                if (scheme == "mailto" || scheme == "tel") {
                    try {
                        act.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    } catch (_: Exception) {
                    }
                }
                return true // el resto (intent:, market:, etc.) no se abre desde acá
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                loading = true
                if (!urlField.hasFocus()) urlField.setText(url ?: "")
                syncUi()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                loading = false
                if (!urlField.hasFocus()) urlField.setText(url ?: "")
                syncUi()
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                if (!urlField.hasFocus()) urlField.setText(url ?: "")
                syncUi()
            }
        }
        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress.setProgressCompat(newProgress, true)
                progress.visibility = if (newProgress < 100) View.VISIBLE else View.INVISIBLE
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
        view.setPadding(0, dp(10), 0, 0)

        // Barra: atrás · adelante · recargar · dirección · cerrar
        val bar = LinearLayout(act)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(dp(6), 0, dp(6), dp(4))
        backBtn = iconButton(R.drawable.ic_back, "Atrás") { if (webView.canGoBack()) webView.goBack() }
        fwdBtn = iconButton(R.drawable.ic_forward, "Adelante") { if (webView.canGoForward()) webView.goForward() }
        reloadBtn = iconButton(R.drawable.ic_reload, "Recargar") { if (loading) webView.stopLoading() else webView.reload() }
        bar.addView(backBtn, LinearLayout.LayoutParams(dp(40), dp(44)))
        bar.addView(fwdBtn, LinearLayout.LayoutParams(dp(40), dp(44)))
        bar.addView(reloadBtn, LinearLayout.LayoutParams(dp(40), dp(44)))

        urlField = EditText(act)
        urlField.setSingleLine(true)
        urlField.textSize = 14f
        urlField.setTextColor(onSurface)
        urlField.setHintTextColor(muted)
        urlField.hint = "Buscá o escribí una dirección"
        urlField.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        urlField.imeOptions = EditorInfo.IME_ACTION_GO
        urlField.setSelectAllOnFocus(true)
        urlField.setPadding(dp(14), dp(8), dp(14), dp(8))
        urlField.background = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()
            setColor(ColorUtils.setAlphaComponent(onSurface, 0x14))
            setStroke(dp(1), ColorUtils.setAlphaComponent(onSurface, 50))
        }
        urlField.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO || (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                go(urlField.text.toString())
                true
            } else false
        }
        bar.addView(urlField, LinearLayout.LayoutParams(0, dp(40), 1f).apply {
            marginStart = dp(4)
            marginEnd = dp(4)
        })
        bar.addView(iconButton(R.drawable.ic_close, "Cerrar el navegador") { host.close() }, LinearLayout.LayoutParams(dp(40), dp(44)))
        view.addView(bar)

        progress = LinearProgressIndicator(act)
        progress.max = 100
        progress.setIndicatorColor(accent)
        progress.visibility = View.INVISIBLE
        view.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // Accesos rápidos
        val scroll = HorizontalScrollView(act)
        scroll.isHorizontalScrollBarEnabled = false
        val chips = LinearLayout(act)
        chips.orientation = LinearLayout.HORIZONTAL
        chips.setPadding(dp(12), dp(6), dp(12), dp(6))
        for ((label, url) in QUICK) chips.addView(chip(label) { go(url) }, chipLp())
        chips.addView(chip("Abrir en el navegador del sistema") { openExternal() }, chipLp())
        scroll.addView(chips, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        view.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        (webView.parent as? ViewGroup)?.removeView(webView)
        view.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun selectableBg(): Int {
        val tv = android.util.TypedValue()
        act.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
        return tv.resourceId
    }

    private fun iconButton(icon: Int, desc: String, onClick: () -> Unit): ImageButton {
        val b = ImageButton(act)
        b.setImageResource(icon)
        b.imageTintList = android.content.res.ColorStateList.valueOf(accent)
        b.setBackgroundResource(selectableBg())
        b.contentDescription = desc
        b.setOnClickListener { onClick() }
        return b
    }

    private fun chipLp() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        marginEnd = dp(8)
    }

    private fun chip(label: String, onClick: () -> Unit): TextView {
        val tv = TextView(act)
        tv.text = label
        tv.textSize = 13f
        tv.setTextColor(onSurface)
        tv.gravity = Gravity.CENTER_VERTICAL
        tv.setPadding(dp(14), dp(7), dp(14), dp(7))
        tv.background = GradientDrawable().apply {
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1), ColorUtils.setAlphaComponent(onSurface, 60))
            setColor(ColorUtils.setAlphaComponent(accent, 0x1A))
        }
        tv.setOnClickListener { onClick() }
        return tv
    }

    /** Habilita/deshabilita atrás y adelante y cambia el ícono de recargar por «detener» mientras carga. */
    private fun syncUi() {
        backBtn.alpha = if (webView.canGoBack()) 1f else 0.3f
        fwdBtn.alpha = if (webView.canGoForward()) 1f else 0.3f
        reloadBtn.setImageResource(if (loading) R.drawable.ic_close else R.drawable.ic_reload)
        reloadBtn.contentDescription = if (loading) "Detener" else "Recargar"
    }

    // ---------------------------------------------------------------- Navegación

    /** Al abrir el cajón: la primera vez carga GitHub; las siguientes conserva la página. */
    fun open() {
        if (!started) {
            started = true
            go(QUICK[0].second)
        }
        syncUi()
    }

    /** Atrás del navegador; devuelve false si no hay a dónde volver (entonces se cierra el cajón). */
    fun onBack(): Boolean {
        if (urlField.hasFocus()) {
            urlField.clearFocus()
            hideKeyboard()
            return true
        }
        if (webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return false
    }

    private fun hideKeyboard() {
        val imm = act.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(urlField.windowToken, 0)
    }

    /** Dirección → https; texto suelto → búsqueda en DuckDuckGo. Solo http y https. */
    private fun go(raw: String) {
        val t = raw.trim()
        if (t.isEmpty()) return
        val url = when {
            t.startsWith("http://", true) || t.startsWith("https://", true) -> t
            t.contains("://") -> {
                host.message("Solo se pueden abrir direcciones http y https")
                return
            }
            !t.contains(' ') && t.contains('.') -> "https://$t"
            else -> "https://duckduckgo.com/?q=" + Uri.encode(t)
        }
        urlField.clearFocus()
        hideKeyboard()
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
        const val PROFILE = "browser"
        val QUICK = listOf(
            "GitHub" to "https://github.com/",
            "Google" to "https://www.google.com/",
            "DuckDuckGo" to "https://duckduckgo.com/",
            "Stack Overflow" to "https://stackoverflow.com/"
        )
    }
}
