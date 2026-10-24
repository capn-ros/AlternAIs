package app.claudeck

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.text.format.DateFormat
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.util.Base64
import android.util.TypedValue
import android.view.Gravity
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.IconCompat
import androidx.core.view.WindowCompat
import androidx.core.view.doOnLayout
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    companion object {
        /** Extra para abrir la app directamente en una cuenta (burbuja, atajos del launcher). */
        const val EXTRA_SELECT = "app.claudeck.extra.SELECT"

        private const val START_URL = "https://claude.ai/"

        /** Tiempo sin tocar la barra de cuentas antes de que se pliegue sola. */
        private const val AUTO_HIDE_MS = 4000L

        /** Nombre del objeto JS que usa la página para pasarnos archivos `blob:` a guardar. */
        private const val BRIDGE = "claudeckDl"
        private val BRIDGE_ORIGINS = setOf(
            "https://claude.ai", "https://*.claude.ai",
            "https://claude.com", "https://*.claude.com"
        )

        private val PALETTE = listOf(
            "#E07A5F", "#F2A541", "#81B29A", "#4FB0C6",
            "#6C8EAD", "#9A7BD1", "#D96C9F", "#8D99AE"
        ).map { Color.parseColor(it) }

        /**
         * Se inyecta en cada página: recuerda los blobs creados (por si la página los revoca antes de
         * que podamos leerlos) y el nombre de archivo del <a download> clickeado.
         */
        private val HOOK_JS = """
            (function(){
              if (window.__ckHook) return; window.__ckHook = true;
              window.__ckBlobs = {}; window.__ckDlName = '';
              var c = URL.createObjectURL.bind(URL);
              URL.createObjectURL = function(o){ var u = c(o); try { window.__ckBlobs[u] = o; } catch(e) {} return u; };
              var r = URL.revokeObjectURL.bind(URL);
              URL.revokeObjectURL = function(u){ setTimeout(function(){ try { r(u); delete window.__ckBlobs[u]; } catch(e) {} }, 60000); };
              var oc = HTMLAnchorElement.prototype.click;
              HTMLAnchorElement.prototype.click = function(){
                try { if (this.hasAttribute('download')) window.__ckDlName = this.getAttribute('download') || ''; } catch(e) {}
                return oc.apply(this, arguments);
              };
              document.addEventListener('click', function(ev){
                try {
                  var a = ev.target && ev.target.closest ? ev.target.closest('a') : null;
                  if (a && a.hasAttribute('download')) window.__ckDlName = a.getAttribute('download') || '';
                } catch(e) {}
              }, true);
            })();
        """.trimIndent()
    }

    private lateinit var store: AccountStore
    private val accounts = mutableListOf<Account>()
    private val webViews = HashMap<String, WebView>()
    private val chips = HashMap<String, ImageView>()
    private val failed = HashSet<String>()
    private val downloads = HashMap<Long, String>()

    private lateinit var container: FrameLayout
    private lateinit var dock: LinearLayout
    private lateinit var infoRow: LinearLayout
    private lateinit var chipGroup: LinearLayout
    private lateinit var chipScroll: HorizontalScrollView
    private lateinit var progress: LinearProgressIndicator
    private lateinit var emptyView: View
    private lateinit var emptyIcon: View
    private lateinit var errorView: View
    private lateinit var info: TextView
    private lateinit var infoDot: View
    private lateinit var btnToggleDock: ImageButton
    private lateinit var btnMore: ImageButton
    private lateinit var dockBg: GradientDrawable
    private var dockCollapsed = false
    private var borderColor = 0
    private var borderAnim: ValueAnimator? = null
    private lateinit var drawer: LinearLayout
    private lateinit var drawerBg: GradientDrawable
    private lateinit var drawerSwitch: MaterialSwitch
    private var drawerOpen = false

    private var currentId: String? = null
    private var previousId: String? = null
    private var pendingFileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingBubble = false
    private var afterNotif: (() -> Unit)? = null
    private var pendingStorage: (() -> Unit)? = null
    private var keyboardOpen = false
    private var emptyAnim: android.animation.ObjectAnimator? = null
    private val handler = Handler(Looper.getMainLooper())

    private val multiProfileOk: Boolean by lazy {
        WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
    }
    private val bridgeOk: Boolean by lazy {
        WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
    }

    private val fileLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            val uris = WebChromeClient.FileChooserParams.parseResult(res.resultCode, res.data)
            pendingFileCallback?.onReceiveValue(uris)
            pendingFileCallback = null
        }

    private val notifLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            val next = afterNotif
            afterNotif = null
            next?.invoke()
        }

    private val storageLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val next = pendingStorage
            pendingStorage = null
            if (granted) next?.invoke()
            else snack("Sin permiso de almacenamiento no se pueden guardar archivos. Podés darlo en Ajustes de la app.")
        }

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: return
            val name = downloads.remove(id) ?: return
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val cur = dm.query(DownloadManager.Query().setFilterById(id))
            cur.use { c ->
                if (c != null && c.moveToFirst()) {
                    val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        snack("Descargado: $name", "Abrir") {
                            openDownloaded(dm.getUriForDownloadedFile(id), dm.getMimeTypeForDownloadedFile(id))
                        }
                    } else {
                        snack("Falló la descarga de $name")
                    }
                }
            }
        }
    }

    private val autoHide = Runnable {
        if (!dockCollapsed && !drawerOpen && accounts.isNotEmpty() && !keyboardOpen) setDockCollapsed(true, true)
    }

    private val ticker = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, 30_000)
        }
    }

    // ---------------------------------------------------------------- Ciclo de vida

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        applySystemBars()
        buildDock()

        store = AccountStore(this)
        accounts.addAll(store.load())

        container = findViewById(R.id.container)
        buildDrawer()
        progress = findViewById(R.id.progress)
        emptyView = findViewById(R.id.emptyView)
        emptyIcon = findViewById(R.id.emptyIcon)
        errorView = findViewById(R.id.errorView)

        findViewById<View>(R.id.btnAddEmpty).setOnClickListener { addAccountFlow() }
        btnToggleDock.setOnClickListener { setDockCollapsed(!dockCollapsed, true) }
        btnMore.setOnClickListener { showMoreMenu() }
        findViewById<MaterialButton>(R.id.btnRetry).setOnClickListener { retryCurrent() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerOpen) {
                    setDrawerOpen(false)
                    return
                }
                val wv = currentId?.let { webViews[it] }
                if (wv != null && wv.canGoBack()) {
                    wv.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        ContextCompat.registerReceiver(
            this, downloadReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )

        setupGestures()
        watchKeyboard()
        setDockCollapsed(false, false) // arranca desplegada y se pliega sola

        rebuildChips(true)
        val start = intent?.getStringExtra(EXTRA_SELECT)?.takeIf { id -> accounts.any { it.id == id } }
            ?: store.selectedId?.takeIf { id -> accounts.any { it.id == id } }
            ?: accounts.firstOrNull()?.id
        if (start != null) select(start)
        updateEmptyState()
        updateShortcuts()

        // Entrada animada del panel de cuentas
        dock.post {
            dock.translationY = dock.height.toFloat()
            dock.animate().translationY(0f).setDuration(420)
                .setInterpolator(DecelerateInterpolator(1.6f)).start()
        }

        if (!multiProfileOk) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Actualizá el WebView")
                .setMessage(
                    "Tu versión de Android System WebView no permite sesiones separadas. " +
                        "Actualizala desde Play Store para poder usar varias cuentas. " +
                        "Mientras tanto solo se puede usar una cuenta."
                )
                .setPositiveButton("Entendido", null)
                .show()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val id = intent.getStringExtra(EXTRA_SELECT) ?: return
        intent.removeExtra(EXTRA_SELECT)
        if (accounts.any { it.id == id } && id != currentId) select(id)
    }

    override fun onResume() {
        super.onResume()
        webViews.values.forEach { it.onResume() }
        handler.post(ticker)
        scheduleAutoHide()
        startEmptyAnim()

        if (pendingBubble && BubbleService.canDraw(this)) {
            pendingBubble = false
            setBubbleEnabled(true)
        } else if (store.bubbleEnabled) {
            if (BubbleService.canDraw(this)) BubbleService.start(this) else store.bubbleEnabled = false
        }
        pendingBubble = false
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        handler.removeCallbacks(autoHide)
        emptyAnim?.cancel()
        webViews.values.forEach { it.onPause() }
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        try {
            unregisterReceiver(downloadReceiver)
        } catch (_: Exception) {
        }
        webViews.values.forEach { wv ->
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.destroy()
        }
        webViews.clear()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- UI helpers

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun applySystemBars() {
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }
    }

    private fun dotDrawable(color: Int, selected: Boolean): GradientDrawable {
        val d = GradientDrawable()
        d.shape = GradientDrawable.OVAL
        d.setColor(color)
        if (selected) {
            val ring = MaterialColors.getColor(
                this, com.google.android.material.R.attr.colorOnSurface, Color.BLACK
            )
            d.setStroke(dp(3), ring)
        }
        return d
    }

    private fun onSurface(): Int =
        MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.BLACK)

    private fun updateEmptyState() {
        val empty = accounts.isEmpty()
        emptyView.visibility = if (empty) View.VISIBLE else View.GONE
        infoRow.visibility = if (empty) View.GONE else View.VISIBLE
        if (empty) startEmptyAnim()
    }

    private fun startEmptyAnim() {
        if (accounts.isNotEmpty()) return
        emptyAnim?.cancel()
        emptyAnim = android.animation.ObjectAnimator.ofFloat(
            emptyIcon, View.TRANSLATION_Y, 0f, -dp(10).toFloat(), 0f
        ).apply {
            duration = 2600
            repeatCount = android.animation.ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun snack(msg: String, action: String? = null, onAction: (() -> Unit)? = null) {
        val s = Snackbar.make(container, msg, Snackbar.LENGTH_LONG)
        if (dock.visibility == View.VISIBLE) s.anchorView = dock
        if (action != null && onAction != null) s.setAction(action) { onAction() }
        s.show()
    }

    private fun pop(v: View) {
        v.animate().cancel()
        v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(110).withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setDuration(180)
                .setInterpolator(OvershootInterpolator(2f)).start()
        }.start()
    }

    // ---------------------------------------------------------------- Gestos, teclado, panel plegable

    private fun setupGestures() {
        val gd = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                setDockCollapsed(!dockCollapsed, true)
                return true
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (abs(vx) > abs(vy) && abs(vx) > 600f) {
                    step(if (vx < 0) 1 else -1)
                    return true
                }
                return false
            }
        })
        infoRow.setOnTouchListener { v, ev ->
            val r = gd.onTouchEvent(ev)
            if (ev.action == MotionEvent.ACTION_UP) v.performClick()
            r
        }
    }

    /** Pasa a la cuenta siguiente (+1) o anterior (-1) de la lista. */
    private fun step(delta: Int) {
        if (accounts.size < 2) return
        val idx = accounts.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
        val next = (idx + delta + accounts.size) % accounts.size
        select(accounts[next].id)
    }

    private fun setDockCollapsed(collapsed: Boolean, animate: Boolean) {
        dockCollapsed = collapsed
        store.dockCollapsed = collapsed
        if (animate) {
            TransitionManager.beginDelayedTransition(dock, AutoTransition().setDuration(220))
        }
        // Plegada: punto de la cuenta (izquierda) y flecha (derecha). Desplegada: nombre, ajustes, círculos y botón +.
        chipScroll.visibility = if (collapsed) View.GONE else View.VISIBLE
        // INVISIBLE (no GONE) para que el punto quede a la izquierda y la flecha a la derecha
        info.visibility = if (collapsed) View.INVISIBLE else View.VISIBLE
        btnMore.visibility = if (collapsed) View.GONE else View.VISIBLE
        if (collapsed) setDrawerOpen(false)
        btnToggleDock.animate().rotation(if (collapsed) 180f else 0f).setDuration(220).start()
        scheduleAutoHide()
    }

    /** Reinicia la cuenta regresiva para plegar la barra sola (si está desplegada). */
    private fun scheduleAutoHide() {
        handler.removeCallbacks(autoHide)
        if (!dockCollapsed && accounts.isNotEmpty()) handler.postDelayed(autoHide, AUTO_HIDE_MS)
    }

    /** Borde superior de la barra con el color de la cuenta activa (separa la barra de la pantalla de Claude). */
    private fun setBorderColor(color: Int) {
        if (color == borderColor) return
        val from = borderColor
        borderColor = color
        borderAnim?.cancel()
        if (from == 0) {
            dockBg.setStroke(dp(3), color)
            if (::drawerBg.isInitialized) drawerBg.setStroke(dp(3), color)
            return
        }
        borderAnim = ValueAnimator.ofObject(ArgbEvaluator(), from, color).apply {
            duration = 260
            addUpdateListener {
                val c = it.animatedValue as Int
                dockBg.setStroke(dp(3), c)
                if (::drawerBg.isInitialized) drawerBg.setStroke(dp(3), c)
            }
            start()
        }
    }

    private fun newTrayBg(): GradientDrawable {
        val fill = MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorSurfaceContainer, Color.WHITE
        )
        val r = dp(22).toFloat()
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            setColor(fill)
            setStroke(
                dp(3),
                if (borderColor != 0) borderColor else ColorUtils.setAlphaComponent(onSurface(), 60)
            )
        }
    }

    /** Bandeja con esquinas superiores redondeadas; el borde de abajo queda fuera y se recorta. */
    private fun styleTray(v: View, bg: GradientDrawable) {
        val r = dp(22).toFloat()
        v.background = LayerDrawable(arrayOf(bg)).apply { setLayerInset(0, 0, 0, 0, -dp(8)) }
        v.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height + dp(24), r)
            }
        }
        v.clipToOutline = true
        v.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ -> view.invalidateOutline() }
    }

    private fun brand(): Int = ContextCompat.getColor(this, R.color.brand)

    private fun applyIcon(tv: TextView, icon: Int) {
        val dr = AppCompatResources.getDrawable(this, icon)!!.mutate()
        dr.setTint(brand())
        dr.setBounds(0, 0, dp(24), dp(24))
        tv.setCompoundDrawablesRelative(dr, null, null, null)
        tv.compoundDrawablePadding = dp(16)
    }

    // ---------------------------------------------------------------- Cajón de ajustes (detrás de la barra)

    private fun buildDrawer() {
        val d = LinearLayout(this)
        d.orientation = LinearLayout.VERTICAL
        d.elevation = dp(6).toFloat()
        d.isClickable = true // que los toques no pasen al WebView de atrás
        d.setPadding(dp(8), dp(16), dp(8), dp(12))
        drawerBg = newTrayBg()
        styleTray(d, drawerBg)

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(dp(16), dp(0), dp(16), dp(4))
        val logo = ImageView(this)
        logo.setImageResource(R.mipmap.ic_launcher)
        logo.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
        logo.clipToOutline = true
        head.addView(logo, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(14) })
        val title = TextView(this)
        title.text = "Claudeck"
        title.textSize = 22f
        title.setTypeface(title.typeface, android.graphics.Typeface.BOLD)
        title.setTextColor(onSurface())
        head.addView(title)
        d.addView(head)

        val caption = TextView(this)
        caption.text = "Ajustes"
        caption.textSize = 13f
        caption.setTextColor(brand())
        caption.setTypeface(caption.typeface, android.graphics.Typeface.BOLD)
        caption.setPadding(dp(16), dp(14), dp(16), dp(2))
        d.addView(caption)

        // Burbuja de cuentas, con interruptor
        val bubbleRow = LinearLayout(this)
        bubbleRow.orientation = LinearLayout.HORIZONTAL
        bubbleRow.gravity = Gravity.CENTER_VERTICAL
        bubbleRow.setPadding(dp(16), dp(6), dp(16), dp(6))
        bubbleRow.setBackgroundResource(selectableBg())
        val label = TextView(this)
        label.text = "Burbuja de cuentas"
        label.textSize = 16f
        label.setTextColor(onSurface())
        applyIcon(label, R.drawable.ic_bubbles)
        bubbleRow.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        drawerSwitch = MaterialSwitch(this)
        drawerSwitch.isClickable = false
        bubbleRow.addView(drawerSwitch)
        bubbleRow.setOnClickListener {
            val turnOn = !drawerSwitch.isChecked
            setDrawerOpen(false)
            setBubbleEnabled(turnOn)
        }
        d.addView(bubbleRow)

        sheetRow(d, null, "Recargar cuenta actual", R.drawable.ic_reload) {
            setDrawerOpen(false)
            currentId?.let { webViews[it]?.reload() }
        }
        sheetRow(d, null, "Ver descargas", R.drawable.ic_download) {
            setDrawerOpen(false)
            try {
                startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
            } catch (_: Exception) {
                snack("No se pudo abrir Descargas")
            }
        }
        sheetRow(d, null, "Permisos de la app", R.drawable.ic_shield) {
            setDrawerOpen(false)
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        }

        d.visibility = View.GONE
        container.addView(
            d,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM
            )
        )
        drawer = d
    }

    /** El cajón sale desde detrás de la barra (el contenedor recorta lo que queda por debajo). */
    private fun setDrawerOpen(open: Boolean) {
        if (open == drawerOpen) return
        drawerOpen = open
        drawer.animate().cancel()
        if (open) {
            drawerSwitch.isChecked = store.bubbleEnabled && BubbleService.canDraw(this)
            drawer.alpha = 0f
            drawer.visibility = View.VISIBLE
            drawer.doOnLayout {
                it.translationY = it.height.toFloat()
                it.alpha = 1f
                it.animate().translationY(0f).setDuration(260)
                    .setInterpolator(DecelerateInterpolator(1.6f)).start()
            }
        } else {
            drawer.animate().translationY(drawer.height.toFloat()).setDuration(200)
                .withEndAction { if (!drawerOpen) drawer.visibility = View.GONE }.start()
        }
    }

    private fun hit(v: View, ev: MotionEvent): Boolean {
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        return ev.rawX >= loc[0] && ev.rawX <= loc[0] + v.width &&
            ev.rawY >= loc[1] && ev.rawY <= loc[1] + v.height
    }

    /** Tocar fuera de la barra (y del cajón) la pliega. El toque sigue su camino al WebView. */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN && ::dock.isInitialized && ::drawer.isInitialized &&
            !dockCollapsed && accounts.isNotEmpty() && dock.visibility == View.VISIBLE &&
            !hit(dock, ev) && !(drawerOpen && hit(drawer, ev))
        ) {
            setDockCollapsed(true, true)
        }
        return super.dispatchTouchEvent(ev)
    }

    /**
     * Reemplaza la barra inferior del layout XML por una armada en código: fila de info (punto,
     * nombre, más, flecha) y debajo círculos de cuenta con inicial + botón + multicolor.
     * Conserva los ids originales, así el resto del layout (Snackbar, posición) sigue igual.
     */
    private fun buildDock() {
        val old = findViewById<View>(R.id.dock)
        val parent = old.parent as ViewGroup
        val index = parent.indexOfChild(old)
        val lp = old.layoutParams
        val elev = if (old.elevation > 0f) old.elevation else dp(8).toFloat()
        parent.removeView(old)

        val d = object : LinearLayout(this@MainActivity) {
            override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                scheduleAutoHide()
                return super.dispatchTouchEvent(ev)
            }
        }
        d.id = R.id.dock
        d.orientation = LinearLayout.VERTICAL
        d.elevation = elev

        // Fondo con esquinas superiores redondeadas y borde de color
        borderColor = 0
        dockBg = newTrayBg()
        styleTray(d, dockBg)

        // Fila de info
        val row = LinearLayout(this)
        row.id = R.id.infoRow
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.minimumHeight = dp(46)
        row.setPadding(dp(16), dp(2), dp(8), dp(0))

        val dot = View(this)
        dot.id = R.id.infoDot
        row.addView(dot, LinearLayout.LayoutParams(dp(16), dp(16)).apply { marginEnd = dp(10) })

        val name = TextView(this)
        name.id = R.id.info
        name.maxLines = 1
        name.ellipsize = android.text.TextUtils.TruncateAt.END
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        name.setTextColor(onSurface())
        row.addView(name, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        fun iconButton(id: Int, res: Int, desc: String): ImageButton {
            val b = ImageButton(this)
            b.id = id
            b.setImageResource(res)
            b.imageTintList = ColorStateList.valueOf(onSurface())
            b.setBackgroundResource(selectableBg())
            b.contentDescription = desc
            return b
        }
        val more = iconButton(R.id.btnMore, R.drawable.ic_settings, "Ajustes")
        row.addView(more, LinearLayout.LayoutParams(dp(42), dp(42)))
        val toggle = iconButton(R.id.btnToggleDock, R.drawable.ic_expand, "Plegar o desplegar")
        row.addView(toggle, LinearLayout.LayoutParams(dp(42), dp(42)))
        d.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // Círculos de cuentas
        val scroll = HorizontalScrollView(this)
        scroll.id = R.id.chipScroll
        scroll.isFillViewport = true
        scroll.isHorizontalScrollBarEnabled = false
        scroll.overScrollMode = View.OVER_SCROLL_NEVER
        val group = LinearLayout(this)
        group.id = R.id.chipGroup
        group.orientation = LinearLayout.HORIZONTAL
        group.gravity = Gravity.CENTER
        group.setPadding(dp(10), dp(4), dp(10), dp(12))
        scroll.addView(group, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        d.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        parent.addView(d, index, lp)

        dock = d
        infoRow = row
        infoDot = dot
        info = name
        btnMore = more
        btnToggleDock = toggle
        chipScroll = scroll
        chipGroup = group
    }

    /** Oculta el panel de cuentas mientras el teclado está abierto, para tener más lugar al escribir. */
    private fun watchKeyboard() {
        val decor = window.decorView
        decor.viewTreeObserver.addOnGlobalLayoutListener {
            val r = Rect()
            decor.getWindowVisibleDisplayFrame(r)
            val hidden = decor.height - r.bottom
            val open = hidden > dp(160)
            if (open != keyboardOpen) {
                keyboardOpen = open
                if (open) {
                    dock.visibility = View.GONE
                } else {
                    dock.alpha = 0f
                    dock.visibility = View.VISIBLE
                    dock.animate().alpha(1f).setDuration(220).start()
                }
            }
        }
    }

    // ---------------------------------------------------------------- Chips

    private val circleSize get() = dp(40)

    private fun rebuildChips(animateIn: Boolean = false) {
        chips.clear()
        chipGroup.removeAllViews()
        val gap = LinearLayout.LayoutParams(circleSize, circleSize).apply {
            marginStart = dp(6)
            marginEnd = dp(6)
        }
        accounts.forEach { acc ->
            val circle = ImageView(this)
            circle.tag = acc.id
            circle.scaleType = ImageView.ScaleType.FIT_CENTER
            circle.setOnClickListener {
                pop(circle)
                select(acc.id)
            }
            circle.setOnLongClickListener {
                showAccountMenu(acc)
                true
            }
            chips[acc.id] = circle
            chipGroup.addView(circle, LinearLayout.LayoutParams(gap))
        }

        // Botón agregar: círculo multicolor con un +
        val add = TextView(this)
        add.text = "+"
        add.gravity = Gravity.CENTER
        add.setTextColor(Color.WHITE)
        add.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
        add.typeface = android.graphics.Typeface.DEFAULT_BOLD
        add.setShadowLayer(3f, 0f, 1f, Color.argb(120, 0, 0, 0))
        add.includeFontPadding = false
        add.contentDescription = "Agregar cuenta"
        add.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            gradientType = GradientDrawable.SWEEP_GRADIENT
            colors = (PALETTE + PALETTE.first()).toIntArray()
        }
        add.setOnClickListener {
            pop(add)
            addAccountFlow()
        }
        chipGroup.addView(add, LinearLayout.LayoutParams(gap))

        refreshCircles()

        if (animateIn) {
            for (i in 0 until chipGroup.childCount) {
                val c = chipGroup.getChildAt(i)
                c.alpha = 0f
                c.scaleX = 0.8f
                c.scaleY = 0.8f
                c.animate().alpha(1f).scaleX(1f).scaleY(1f)
                    .setStartDelay(160L + i * 45L).setDuration(300)
                    .setInterpolator(OvershootInterpolator(1.6f)).start()
            }
        }
    }

    /** Redibuja cada círculo: color de la cuenta, inicial, aro si es la activa, punto ámbar si descansa. */
    private fun refreshCircles() {
        accounts.forEach { acc ->
            val iv = chips[acc.id] ?: return@forEach
            iv.setImageBitmap(
                Ui.avatar(
                    acc.color, Ui.initial(acc.name), circleSize,
                    ring = acc.id == currentId, resting = acc.limitActive(), ringColor = onSurface()
                )
            )
            iv.contentDescription = if (acc.limitActive()) "${acc.name} (en descanso)" else acc.name
        }
    }

    private fun refreshChipTexts() = refreshCircles()

    private fun updateSelection() = refreshCircles()

    private fun updateInfo() {
        val acc = accounts.firstOrNull { it.id == currentId }
        if (acc == null) {
            info.text = ""
            return
        }
        setBorderColor(acc.color)
        btnToggleDock.imageTintList = ColorStateList.valueOf(acc.color)
        val dot = GradientDrawable()
        dot.shape = GradientDrawable.OVAL
        dot.setColor(acc.color)
        infoDot.background = dot

        val parts = mutableListOf(acc.name)
        if (acc.purpose.isNotBlank()) parts.add(acc.purpose)
        if (acc.limitActive()) {
            val until = DateFormat.format("EEE HH:mm", acc.limitUntil)
            val left = formatRemaining(acc.limitUntil - System.currentTimeMillis())
            parts.add("En descanso hasta $until (faltan $left)")
        } else {
            parts.add("Disponible")
        }
        info.text = parts.joinToString(" · ")
    }

    private fun formatRemaining(ms: Long): String {
        val totalMin = (ms / 60_000L).coerceAtLeast(1L)
        val days = totalMin / (60 * 24)
        val hours = (totalMin % (60 * 24)) / 60
        val mins = totalMin % 60
        return when {
            days > 0 -> "$days d $hours h"
            hours > 0 -> "$hours h $mins min"
            else -> "$mins min"
        }
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        var changed = false
        accounts.forEach { a ->
            if (a.limitUntil in 1L..now) {
                a.limitUntil = 0L
                changed = true
                Toast.makeText(this, "«${a.name}» ya puede usarse de nuevo", Toast.LENGTH_LONG).show()
            }
        }
        if (changed) {
            store.save(accounts)
            BubbleService.refresh()
        }
        refreshChipTexts()
        updateInfo()
    }

    // ---------------------------------------------------------------- Selección y WebViews

    private fun select(id: String) {
        val acc = accounts.firstOrNull { it.id == id } ?: return
        val prev = currentId
        if (prev != null && prev != id) previousId = prev
        val oldIdx = accounts.indexOfFirst { it.id == prev }
        val newIdx = accounts.indexOfFirst { it.id == id }
        currentId = id
        store.selectedId = id
        webViews.getOrPut(id) { createWebView(acc) }
        transitionTo(id, if (oldIdx < 0) 0 else Integer.signum(newIdx - oldIdx))
        progress.setIndicatorColor(acc.color)
        progress.visibility = View.INVISIBLE
        updateSelection()
        updateInfo()
        updateErrorView()
        chips[id]?.let { chip -> chipScroll.post { chipScroll.smoothScrollTo(chip.left - dp(16), 0) } }
        webViews[id]?.requestFocus()
        scheduleAutoHide()
        BubbleService.refresh()

        if (acc.limitActive() && prev != null) {
            val alt = accounts.firstOrNull { it.id != acc.id && !it.limitActive() }
            handler.post {
                if (alt != null) snack("«${acc.name}» está en descanso", "Ir a «${alt.name}»") { select(alt.id) }
                else snack("«${acc.name}» está en descanso por límite de uso")
            }
        }
    }

    /** Cambio de cuenta con fundido y un pequeño deslizamiento según el sentido. */
    private fun transitionTo(id: String, dir: Int) {
        val shift = dp(28).toFloat() * dir
        webViews.forEach { (k, v) ->
            v.animate().cancel()
            if (k == id) {
                if (v.visibility != View.VISIBLE) {
                    v.alpha = 0f
                    v.translationX = shift
                    v.visibility = View.VISIBLE
                }
                v.animate().alpha(1f).translationX(0f).setDuration(240)
                    .setInterpolator(DecelerateInterpolator()).start()
            } else if (v.visibility == View.VISIBLE) {
                v.animate().alpha(0f).translationX(-shift).setDuration(180).withEndAction {
                    if (currentId != k) v.visibility = View.GONE
                    v.translationX = 0f
                }.start()
            }
        }
    }

    private fun updateErrorView() {
        errorView.visibility = if (currentId != null && failed.contains(currentId)) View.VISIBLE else View.GONE
    }

    private fun retryCurrent() {
        val id = currentId ?: return
        failed.remove(id)
        updateErrorView()
        webViews[id]?.let { wv ->
            wv.reload()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(acc: Account): WebView {
        val wv = WebView(this)
        wv.tag = acc.id
        if (multiProfileOk) {
            // Perfil propio: cookies, almacenamiento y caché separados por cuenta.
            ProfileStore.getInstance().getOrCreateProfile(acc.profileName)
            WebViewCompat.setProfile(wv, acc.profileName)
        }
        wv.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
        wv.setBackgroundColor(Color.TRANSPARENT)
        wv.visibility = View.GONE // lo muestra transitionTo()
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            mediaPlaybackRequiresUserGesture = true
        }
        if (bridgeOk) {
            try {
                // Puente restringido a claude.ai: la página nos entrega los archivos blob: a guardar.
                WebViewCompat.addWebMessageListener(wv, BRIDGE, BRIDGE_ORIGINS) { _, message, _, _, _ ->
                    onBridgeMessage(message.data)
                }
            } catch (_: Exception) {
            }
        }
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                val scheme = uri.scheme ?: return false
                if (scheme == "http" || scheme == "https") return false
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                } catch (_: Exception) {
                }
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                view?.evaluateJavascript(HOOK_JS, null)
                val id = view?.tag as? String ?: return
                if (failed.remove(id) && id == currentId) updateErrorView()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                view?.evaluateJavascript(HOOK_JS, null)
            }

            override fun onReceivedError(
                view: WebView?, request: WebResourceRequest?, error: WebResourceError?
            ) {
                if (request?.isForMainFrame != true) return
                val id = view?.tag as? String ?: return
                failed.add(id)
                if (id == currentId) updateErrorView()
            }
        }
        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (view?.tag != currentId) return
                progress.setProgressCompat(newProgress, true)
                progress.visibility = if (newProgress < 100) View.VISIBLE else View.INVISIBLE
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                if (filePathCallback == null || fileChooserParams == null) return false
                pendingFileCallback?.onReceiveValue(null)
                pendingFileCallback = filePathCallback
                return try {
                    fileLauncher.launch(fileChooserParams.createIntent())
                    true
                } catch (e: Exception) {
                    pendingFileCallback = null
                    filePathCallback.onReceiveValue(null)
                    false
                }
            }
        }
        wv.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            handleDownload(wv, url, userAgent, contentDisposition, mimeType)
        }
        container.addView(wv, 0)
        wv.loadUrl(START_URL)
        return wv
    }

    // ---------------------------------------------------------------- Descargas

    private fun handleDownload(wv: WebView, url: String, ua: String?, cd: String?, mime: String?) {
        when {
            url.startsWith("blob:") -> withStoragePermission { fetchBlob(wv, url) }
            url.startsWith("data:") -> withStoragePermission { saveDataUrl(url) }
            else -> withStoragePermission { enqueueDownload(wv.tag as? String, url, ua, cd, mime) }
        }
    }

    /** En Android 9 o menor hace falta pedir permiso de almacenamiento; en 10+ no. */
    private fun withStoragePermission(block: () -> Unit) {
        if (Build.VERSION.SDK_INT >= 29 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            block()
        } else {
            pendingStorage = block
            storageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    private fun cookiesFor(accId: String?, url: String): String? {
        try {
            if (multiProfileOk && accId != null) {
                val acc = accounts.firstOrNull { it.id == accId }
                if (acc != null) {
                    val c = ProfileStore.getInstance().getProfile(acc.profileName)?.cookieManager?.getCookie(url)
                    if (!c.isNullOrEmpty()) return c
                }
            }
            return android.webkit.CookieManager.getInstance().getCookie(url)
        } catch (_: Exception) {
            return null
        }
    }

    /** Descarga normal (http/https) con el DownloadManager del sistema, usando las cookies de la cuenta. */
    private fun enqueueDownload(accId: String?, url: String, ua: String?, cd: String?, mime: String?) {
        try {
            val name = URLUtil.guessFileName(url, cd, mime)
            val req = DownloadManager.Request(Uri.parse(url))
            if (!mime.isNullOrBlank()) req.setMimeType(mime)
            val cookie = cookiesFor(accId, url)
            if (!cookie.isNullOrEmpty()) req.addRequestHeader("Cookie", cookie)
            if (!ua.isNullOrEmpty()) req.addRequestHeader("User-Agent", ua)
            req.setTitle(name)
            req.setDescription("Claudeck")
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val id = dm.enqueue(req)
            downloads[id] = name
            snack("Descargando $name…")
        } catch (e: Exception) {
            snack("No se pudo descargar el archivo")
        }
    }

    /** Pide a la página (vía JS) el contenido del blob y lo recibe en [onBridgeMessage]. */
    private fun fetchBlob(wv: WebView, url: String) {
        if (!bridgeOk) {
            snack("Este WebView no permite guardar este tipo de descarga. Actualizalo desde Play Store.")
            return
        }
        val js = """
            (function(u){
              var name = window.__ckDlName || ''; window.__ckDlName = '';
              var p = (window.__ckBlobs && window.__ckBlobs[u]) ? Promise.resolve(window.__ckBlobs[u])
                      : fetch(u).then(function(r){ return r.blob(); });
              p.then(function(b){
                var fr = new FileReader();
                fr.onloadend = function(){
                  var s = String(fr.result || ''); var i = s.indexOf(',');
                  $BRIDGE.postMessage(JSON.stringify({name: name, mime: b.type || '', data: i >= 0 ? s.substring(i + 1) : ''}));
                };
                fr.readAsDataURL(b);
              }).catch(function(e){ $BRIDGE.postMessage(JSON.stringify({error: String(e)})); });
            })(${JSONObject.quote(url)});
        """.trimIndent()
        wv.evaluateJavascript(js, null)
        snack("Preparando descarga…")
    }

    private fun onBridgeMessage(raw: String?) {
        if (raw == null) return
        Thread {
            try {
                val o = JSONObject(raw)
                if (o.has("error")) {
                    runOnUiThread { snack("No se pudo descargar el archivo") }
                    return@Thread
                }
                val mime = o.optString("mime").ifBlank { "application/octet-stream" }
                val name = cleanFileName(o.optString("name"), mime)
                val bytes = Base64.decode(o.optString("data"), Base64.DEFAULT)
                val uri = saveToDownloads(name, mime, bytes)
                runOnUiThread { onSaved(name, mime, uri) }
            } catch (e: Exception) {
                runOnUiThread { snack("No se pudo guardar el archivo") }
            }
        }.start()
    }

    private fun saveDataUrl(url: String) {
        Thread {
            try {
                val comma = url.indexOf(',')
                val header = url.substring(5, comma)
                val mime = header.substringBefore(';').ifBlank { "application/octet-stream" }
                val payload = url.substring(comma + 1)
                val bytes = if (header.contains(";base64")) Base64.decode(payload, Base64.DEFAULT)
                else Uri.decode(payload).toByteArray()
                val name = cleanFileName("", mime)
                val uri = saveToDownloads(name, mime, bytes)
                runOnUiThread { onSaved(name, mime, uri) }
            } catch (e: Exception) {
                runOnUiThread { snack("No se pudo guardar el archivo") }
            }
        }.start()
    }

    private fun cleanFileName(raw: String, mime: String): String {
        var n = raw.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        if (n.isEmpty()) {
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin"
            n = "claude_${System.currentTimeMillis()}.$ext"
        }
        return n
    }

    /** Guarda en la carpeta Descargas pública. Devuelve la Uri del archivo (o null si no se pudo). */
    private fun saveToDownloads(name: String, mime: String, bytes: ByteArray): Uri? {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
            return uri
        }
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        dir.mkdirs()
        var f = File(dir, name)
        var n = 1
        while (f.exists()) {
            val dot = name.lastIndexOf('.')
            val base = if (dot > 0) name.substring(0, dot) else name
            val ext = if (dot > 0) name.substring(dot) else ""
            f = File(dir, "$base ($n)$ext")
            n++
        }
        f.writeBytes(bytes)
        MediaScannerConnection.scanFile(this, arrayOf(f.absolutePath), arrayOf(mime), null)
        return Uri.fromFile(f)
    }

    private fun onSaved(name: String, mime: String, uri: Uri?) {
        if (uri == null) {
            snack("No se pudo guardar $name")
            return
        }
        snack("Guardado en Descargas: $name", "Abrir") { openDownloaded(uri, mime) }
    }

    private fun openDownloaded(uri: Uri?, mime: String?) {
        try {
            if (uri == null || uri.scheme == "file") {
                startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            }
            val i = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mime ?: "*/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(i)
        } catch (_: Exception) {
            try {
                startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
            } catch (_: Exception) {
                snack("No hay una app para abrir este archivo")
            }
        }
    }

    // ---------------------------------------------------------------- Cuentas

    private fun onAccountsChanged() {
        store.save(accounts)
        updateShortcuts()
        BubbleService.refresh()
    }

    private fun addAccountFlow() {
        if (!multiProfileOk && accounts.isNotEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Falta actualizar el WebView")
                .setMessage("Para usar más de una cuenta hay que actualizar Android System WebView desde Play Store.")
                .setPositiveButton("Entendido", null)
                .show()
            return
        }
        showAccountDialog(null)
    }

    private fun showAccountDialog(existing: Account?) {
        val v = layoutInflater.inflate(R.layout.dialog_account, null)
        val inName = v.findViewById<TextInputEditText>(R.id.inputName)
        val inPurpose = v.findViewById<TextInputEditText>(R.id.inputPurpose)
        val row = v.findViewById<LinearLayout>(R.id.colorRow)
        var color = existing?.color ?: PALETTE[accounts.size % PALETTE.size]
        inName.setText(existing?.name ?: "")
        inPurpose.setText(existing?.purpose ?: "")

        val dots = mutableListOf<Pair<Int, View>>()
        fun paint() {
            dots.forEach { (c, view) -> view.background = dotDrawable(c, c == color) }
        }
        PALETTE.forEach { c ->
            val dot = View(this)
            val lp = LinearLayout.LayoutParams(dp(40), dp(40))
            lp.setMargins(dp(4), dp(4), dp(4), dp(4))
            dot.layoutParams = lp
            dot.setOnClickListener {
                color = c
                paint()
                pop(dot)
            }
            row.addView(dot)
            dots.add(c to dot)
        }
        paint()

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (existing == null) "Nueva cuenta" else "Editar cuenta")
            .setView(v)
            .setPositiveButton("Guardar", null)
            .setNegativeButton("Cancelar", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = inName.text?.toString()?.trim().orEmpty()
                if (name.isEmpty()) {
                    inName.error = "Poné un nombre"
                    return@setOnClickListener
                }
                val purpose = inPurpose.text?.toString()?.trim().orEmpty()
                if (existing == null) {
                    val acc = Account(Account.newId(), name, purpose, color)
                    accounts.add(acc)
                    onAccountsChanged()
                    rebuildChips()
                    updateEmptyState()
                    select(acc.id)
                    maybeSuggestBubble()
                } else {
                    existing.name = name
                    existing.purpose = purpose
                    existing.color = color
                    onAccountsChanged()
                    rebuildChips()
                    updateInfo()
                    if (existing.id == currentId) progress.setIndicatorColor(existing.color)
                }
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    /** Al tener la segunda cuenta se sugiere (una sola vez) la burbuja flotante. */
    private fun maybeSuggestBubble() {
        if (accounts.size >= 2 && !store.bubbleTipShown && !store.bubbleEnabled) {
            store.bubbleTipShown = true
            handler.postDelayed({
                snack("Tip: la burbuja flotante te deja cambiar de cuenta desde cualquier pantalla", "Activar") {
                    setBubbleEnabled(true)
                }
            }, 600)
        }
    }

    // ---------------------------------------------------------------- Menús (hojas inferiores)

    private fun selectableBg(): Int {
        val tv = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
        return tv.resourceId
    }

    private fun sheetColumn(): LinearLayout {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(8), dp(12), dp(8), dp(24))
        return col
    }

    private fun sheetHeader(col: LinearLayout, title: String, subtitle: String?, color: Int?) {
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = android.view.Gravity.CENTER_VERTICAL
        head.setPadding(dp(16), dp(4), dp(16), dp(12))
        if (color != null) {
            val dot = View(this)
            dot.background = dotDrawable(color, false)
            head.addView(dot, LinearLayout.LayoutParams(dp(14), dp(14)).apply { marginEnd = dp(12) })
        }
        val texts = LinearLayout(this)
        texts.orientation = LinearLayout.VERTICAL
        val t = TextView(this)
        t.text = title
        t.textSize = 18f
        t.setTypeface(t.typeface, android.graphics.Typeface.BOLD)
        t.setTextColor(onSurface())
        texts.addView(t)
        if (!subtitle.isNullOrBlank()) {
            val s = TextView(this)
            s.text = subtitle
            s.textSize = 13f
            s.setTextColor(ColorUtils.setAlphaComponent(onSurface(), 150))
            texts.addView(s)
        }
        head.addView(texts)
        col.addView(head)
    }

    private fun sheetRow(col: LinearLayout, sheet: BottomSheetDialog?, text: String, icon: Int? = null, onClick: () -> Unit): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.textSize = 16f
        tv.setTextColor(onSurface())
        tv.gravity = Gravity.CENTER_VERTICAL
        tv.setPadding(dp(16), dp(14), dp(16), dp(14))
        if (icon != null) applyIcon(tv, icon)
        tv.setBackgroundResource(selectableBg())
        tv.setOnClickListener {
            sheet?.dismiss()
            onClick()
        }
        col.addView(tv)
        return tv
    }

    private fun showAccountMenu(acc: Account) {
        val sheet = BottomSheetDialog(this)
        val col = sheetColumn()
        val status = if (acc.limitActive()) {
            "En descanso hasta " + DateFormat.format("EEE HH:mm", acc.limitUntil)
        } else "Disponible"
        sheetHeader(col, acc.name, listOf(acc.purpose, status).filter { it.isNotBlank() }.joinToString(" · "), acc.color)
        sheetRow(col, sheet, "Editar nombre, propósito o color", R.drawable.ic_edit) { showAccountDialog(acc) }
        sheetRow(col, sheet, "Recargar", R.drawable.ic_reload) { webViews[acc.id]?.reload() ?: select(acc.id) }
        sheetRow(col, sheet, "Marcar límite alcanzado…", R.drawable.ic_clock) { showLimitDialog(acc) }
        if (acc.limitActive()) sheetRow(col, sheet, "Quitar marca de límite", R.drawable.ic_check) { setLimit(acc, 0L) }
        sheetRow(col, sheet, "Cerrar sesión y borrar datos", R.drawable.ic_logout) {
            confirm(
                "Cerrar sesión",
                "Se cierra la sesión de «${acc.name}» y se borran sus cookies y datos en este dispositivo."
            ) { wipeAccount(acc, remove = false) }
        }
        sheetRow(col, sheet, "Eliminar cuenta", R.drawable.ic_trash) {
            confirm(
                "Eliminar cuenta",
                "Se quita «${acc.name}» de la app y se borran sus datos en este dispositivo. Tu cuenta de Claude no se toca."
            ) { wipeAccount(acc, remove = true) }
        }
        sheet.setContentView(col)
        sheet.show()
    }

    private fun showMoreMenu() = setDrawerOpen(!drawerOpen)

    private fun confirm(title: String, message: String, onYes: () -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Sí") { _, _ -> onYes() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ---------------------------------------------------------------- Burbuja flotante

    private fun setBubbleEnabled(on: Boolean) {
        if (!on) {
            store.bubbleEnabled = false
            BubbleService.stop(this)
            snack("Burbuja oculta")
            return
        }
        if (!BubbleService.canDraw(this)) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Permiso necesario")
                .setMessage(
                    "Para mostrar la burbuja sobre otras apps, activá «Mostrar sobre otras apps» " +
                        "para Claudeck en la pantalla que se abre y volvé."
                )
                .setPositiveButton("Abrir ajustes") { _, _ ->
                    pendingBubble = true
                    startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    )
                }
                .setNegativeButton("Cancelar", null)
                .show()
            return
        }
        askNotificationPermissionThen {
            store.bubbleEnabled = true
            BubbleService.start(this)
            snack("Burbuja activada: arrastrala donde quieras y tocala para cambiar de cuenta")
        }
    }

    private fun askNotificationPermissionThen(block: () -> Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            afterNotif = block
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            block()
        }
    }

    // ---------------------------------------------------------------- Atajos del launcher

    /** Mantener apretado el ícono de la app → abrir directo en una cuenta (hasta 4). */
    private fun updateShortcuts() {
        try {
            val list = accounts.take(4).map { acc ->
                ShortcutInfoCompat.Builder(this, "acc_${acc.id}")
                    .setShortLabel(acc.name)
                    .setLongLabel(acc.name)
                    .setIcon(IconCompat.createWithBitmap(Ui.avatar(acc.color, Ui.initial(acc.name), dp(96))))
                    .setIntent(
                        Intent(this, MainActivity::class.java)
                            .setAction(Intent.ACTION_VIEW)
                            .putExtra(EXTRA_SELECT, acc.id)
                    )
                    .build()
            }
            ShortcutManagerCompat.setDynamicShortcuts(this, list)
        } catch (_: Exception) {
        }
    }

    // ---------------------------------------------------------------- Límites de uso

    private fun showLimitDialog(acc: Account) {
        val hour = 60 * 60 * 1000L
        val items = arrayOf("Dentro de 5 horas", "Dentro de 24 horas", "Dentro de 7 días", "A una hora exacta…")
        MaterialAlertDialogBuilder(this)
            .setTitle("¿Cuándo se reinicia el límite?")
            .setItems(items) { _, which ->
                val now = System.currentTimeMillis()
                when (which) {
                    0 -> setLimit(acc, now + 5 * hour)
                    1 -> setLimit(acc, now + 24 * hour)
                    2 -> setLimit(acc, now + 7 * 24 * hour)
                    3 -> pickExactTime(acc)
                }
            }
            .show()
    }

    private fun pickExactTime(acc: Account) {
        val now = Calendar.getInstance()
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(now.get(Calendar.HOUR_OF_DAY))
            .setMinute(now.get(Calendar.MINUTE))
            .setTitleText("Hora en que se reinicia")
            .build()
        picker.addOnPositiveButtonClickListener {
            val target = Calendar.getInstance()
            target.set(Calendar.HOUR_OF_DAY, picker.hour)
            target.set(Calendar.MINUTE, picker.minute)
            target.set(Calendar.SECOND, 0)
            target.set(Calendar.MILLISECOND, 0)
            if (target.timeInMillis <= System.currentTimeMillis()) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }
            setLimit(acc, target.timeInMillis)
        }
        picker.show(supportFragmentManager, "limit_time")
    }

    private fun setLimit(acc: Account, until: Long) {
        acc.limitUntil = until
        onAccountsChanged()
        refreshChipTexts()
        updateInfo()
        if (until > 0L && acc.id == currentId) {
            val alt = accounts.firstOrNull { it.id != acc.id && !it.limitActive() }
            if (alt != null) snack("«${acc.name}» en descanso", "Ir a «${alt.name}»") { select(alt.id) }
        }
    }

    // ---------------------------------------------------------------- Borrado

    private fun wipeAccount(acc: Account, remove: Boolean) {
        webViews.remove(acc.id)?.let { wv ->
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.destroy()
        }
        failed.remove(acc.id)
        if (multiProfileOk) {
            try {
                ProfileStore.getInstance().deleteProfile(acc.profileName)
            } catch (e: Exception) {
                // Si el perfil sigue "en uso", al menos vaciamos cookies y almacenamiento.
                try {
                    val p = ProfileStore.getInstance().getProfile(acc.profileName)
                    p?.cookieManager?.removeAllCookies(null)
                    p?.webStorage?.deleteAllData()
                } catch (_: Exception) {
                }
            }
        }
        if (remove) {
            accounts.removeAll { it.id == acc.id }
            onAccountsChanged()
            rebuildChips()
            updateEmptyState()
            if (previousId == acc.id) previousId = null
            if (currentId == acc.id) {
                currentId = null
                val next = accounts.firstOrNull()?.id
                if (next != null) select(next) else {
                    store.selectedId = null
                    updateInfo()
                    updateErrorView()
                }
            }
        } else {
            select(acc.id)
        }
    }
}
