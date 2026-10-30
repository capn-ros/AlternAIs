package app.claudeck

import androidx.activity.viewModels
import app.claudeck.domain.model.AiService
import app.claudeck.ui.limits.LimitActions
import app.claudeck.ui.limits.LimitViewModel
import app.claudeck.ui.limits.createLimitHost
import app.claudeck.ui.settings.SettingsActions
import app.claudeck.ui.settings.SettingsViewModel
import app.claudeck.ui.settings.createSettingsView
import app.claudeck.ui.snippets.SnippetsViewModel
import dagger.hilt.android.AndroidEntryPoint
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
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
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
import android.view.ContextThemeWrapper
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
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import kotlin.math.abs

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    /** Ajustes en Compose: su estado vive en el ViewModel y se actualiza solo con lo guardado. */
    private val settingsVm: SettingsViewModel by viewModels()

    /** Mensajes guardados en Compose: la lista vive en el ViewModel y se actualiza sola. */
    private val snippetsVm: SnippetsViewModel by viewModels()

    /** Diálogos de «marcar límite» en Compose: el estado de cuál está abierto vive en el ViewModel. */
    private val limitVm: LimitViewModel by viewModels()
    private var limitHost: android.view.View? = null

    companion object {
        /** Extra para abrir la app directamente en una cuenta (burbuja, atajos del launcher). */
        const val EXTRA_SELECT = "app.claudeck.extra.SELECT"

        /** Extra para abrir el selector de respuesta rápida de una cuenta (botón «Responder» de la notificación). */
        const val EXTRA_REPLY = "app.claudeck.extra.REPLY"
        const val EXTRA_SEND_TEXT = "app.claudeck.extra.SEND_TEXT"

        /** La pantalla principal está viva (la alarma lo usa para decir si el mensaje automático se enviará solo). */
        @Volatile
        var alive = false

        /** Tiempo sin tocar la barra de cuentas antes de que se pliegue sola. */
        private const val AUTO_HIDE_MS = 4000L

        /** Nombre del objeto JS que usa la página para pasarnos archivos `blob:` a guardar. */
        private const val BRIDGE = "claudeckDl"
        private val BRIDGE_ORIGINS = setOf(
            "https://claude.ai", "https://*.claude.ai",
            "https://claude.com", "https://*.claude.com",
            "https://replit.com", "https://*.replit.com"
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

    // Logo animado en bucle mientras carga la primera vez (o al recargar) la página de una cuenta.
    private val loadingIds = HashSet<String>()
    private var loadingView: SplashLogoView? = null
    private var loadingShownAt = 0L
    private val loadingSafety = Runnable { loadingIds.clear(); updateLoadingOverlay() }
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
    private lateinit var btnSnippets: ImageButton
    private lateinit var btnFiles: ImageButton
    private lateinit var btnBrowser: ImageButton
    private lateinit var timeBar: TimeBar
    private lateinit var dockBg: GradientDrawable
    private lateinit var dockHandle: View
    private var dockCollapsed = false
    private var borderColor = 0
    private var borderAnim: ValueAnimator? = null
    private lateinit var drawer: LinearLayout
    private lateinit var drawerBg: GradientDrawable
    private var drawerOpen = false
    private var dockRadius = 0f
    private lateinit var dockLayer: LayerDrawable
    private val drawerIcons = mutableListOf<TextView>()
    private var drawerCaption: TextView? = null
    private var drawerAboutRow: TextView? = null
    private var tabClaude: TextView? = null
    private var tabReplit: TextView? = null

    private var currentId: String? = null
    private var previousId: String? = null
    private var pendingFileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingBubble = false
    private var afterNotif: (() -> Unit)? = null
    private var pendingStorage: (() -> Unit)? = null
    private var keyboardOpen = false
    private var tutorialActive = false
    private var tutorialIndex = 0
    private var tutorialOverlay: TutorialOverlay? = null
    private var emptyAnim: android.animation.ObjectAnimator? = null
    private val handler = Handler(Looper.getMainLooper())

    private val multiProfileOk: Boolean by lazy {
        WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
    }
    private val bridgeOk: Boolean by lazy {
        WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
    }

    /** Selector de archivos del sistema permitiendo elegir varios a la vez (aunque la página no lo pida). */
    /**
     * Una página pide subir archivos: se elige con el explorador interno (varios a la vez). Si no hay acceso
     * a los archivos, o desde el explorador se toca «Selector del sistema», se usa el selector de Android.
     */
    private var pickCallback: ValueCallback<Array<Uri>>? = null
    private var pickParams: WebChromeClient.FileChooserParams? = null
    private var pickBackToBrowser = false

    private fun chooseFiles(cb: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams): Boolean {
        pendingFileCallback?.onReceiveValue(null)
        pendingFileCallback = null
        pickCallback?.onReceiveValue(null)
        pickCallback = null
        explorer?.dropPick()
        if (!hasAllFilesAccess()) return systemPick(cb, params)
        pickCallback = cb
        pickParams = params
        pickBackToBrowser = browserOpen
        if (!explorerOpen) setExplorerOpen(true)
        explorer?.startPick(params.acceptTypes?.toList().orEmpty()) { files ->
            val c = pickCallback
            pickCallback = null
            pickParams = null
            c?.onReceiveValue(files?.map { Uri.fromFile(it) }?.toTypedArray())
            if (pickBackToBrowser) {
                pickBackToBrowser = false
                handler.postDelayed({ if (!explorerOpen) setBrowserOpen(true) }, 320)
            }
        }
        return true
    }

    private fun systemPick(cb: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams): Boolean {
        pendingFileCallback = cb
        return try {
            fileLauncher.launch(multiPick(params))
            true
        } catch (e: Exception) {
            pendingFileCallback = null
            cb.onReceiveValue(null)
            false
        }
    }

    private fun switchToSystemPicker() {
        val cb = pickCallback ?: return
        val params = pickParams ?: return
        pickCallback = null
        pickParams = null
        explorer?.dropPick()
        setExplorerOpen(false)
        if (pickBackToBrowser) {
            pickBackToBrowser = false
            handler.postDelayed({ setBrowserOpen(true) }, 320)
        }
        systemPick(cb, params)
    }

    private fun multiPick(params: WebChromeClient.FileChooserParams): Intent =
        params.createIntent().putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)

    private val fileLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            val uris = WebChromeClient.FileChooserParams.parseResult(res.resultCode, res.data)
            pendingFileCallback?.onReceiveValue(uris)
            pendingFileCallback = null
        }

    // Exportar / importar las cuentas (sin sesiones) como archivo .json
    @Suppress("DEPRECATION")
    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument()) { uri -> if (uri != null) writeExport(uri) }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) readImport(uri) }

    private val notifLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            val next = afterNotif
            afterNotif = null
            next?.invoke()
        }

    /** Micrófono para las IA: la página lo pide, Android pregunta una vez y después queda concedido. */
    private var pendingMic: android.webkit.PermissionRequest? = null
    private val micLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val req = pendingMic
            pendingMic = null
            if (req == null) return@registerForActivityResult
            if (granted) {
                req.grant(arrayOf(android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE))
            } else {
                req.deny()
                snack("Sin permiso de micrófono no funciona el dictado. Podés darlo en Ajustes de la app.")
            }
        }

    private fun handleWebPermission(req: android.webkit.PermissionRequest) {
        val host = req.origin?.host.orEmpty()
        val known = req.origin?.scheme == "https" &&
            Service.values().any { s -> s.hosts.any { h -> host == h || host.endsWith(".$h") } }
        val audio = android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE
        if (!known || !req.resources.contains(audio)) {
            req.deny()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            req.grant(arrayOf(audio))
        } else {
            pendingMic?.deny()
            pendingMic = req
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
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
        if (!tutorialActive && !dockCollapsed && !drawerOpen && !subPanelOpen() && accounts.isNotEmpty() && !keyboardOpen) {
            setDockCollapsed(true, true)
        }
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
        store = AccountStore(this)
        applySystemBars()
        buildDock()

        accounts.addAll(store.load())

        container = findViewById(R.id.container)
        buildDrawer()
        progress = findViewById(R.id.progress)
        emptyView = findViewById(R.id.emptyView)
        emptyIcon = findViewById(R.id.emptyIcon)
        errorView = findViewById(R.id.errorView)
        applyServiceTheme()

        if (savedInstanceState == null) showSplash()

        findViewById<View>(R.id.btnAddEmpty).setOnClickListener { addAccountFlow() }
        btnToggleDock.setOnClickListener { setDockCollapsed(!dockCollapsed, true) }
        btnMore.setOnClickListener { showMoreMenu() }
        btnSnippets.setOnClickListener { showSnippets() }
        btnFiles.setOnClickListener { setExplorerOpen(!explorerOpen) }
        btnBrowser.setOnClickListener { setBrowserOpen(!browserOpen) }
        findViewById<MaterialButton>(R.id.btnRetry).setOnClickListener { retryCurrent() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (tutorialActive) {
                    finishTutorial()
                    return
                }
                if (browserOpen) {
                    if (browser?.onBack() != true) setBrowserOpen(false)
                    return
                }
                if (explorerOpen) {
                    if (explorer?.onBack() != true) setExplorerOpen(false)
                    return
                }
                if (snippetsOpen) {
                    setSnippetsOpen(false)
                    return
                }
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
        alive = true
        handleReplyIntent(intent)
        updateEmptyState()
        updateShortcuts()
        if (!store.tutorialShown) {
            if (accounts.isEmpty()) {
                findViewById<View>(android.R.id.content).postDelayed({ startTutorial() }, 500)
            } else {
                // No interrumpir a quienes ya usan una versión anterior de AlternAIs.
                store.tutorialShown = true
            }
        }

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
        val id = intent.getStringExtra(EXTRA_SELECT)
        if (id != null) {
            intent.removeExtra(EXTRA_SELECT)
            if (accounts.any { it.id == id } && id != currentId) select(id)
        }
        handleReplyIntent(intent)
    }

    /** Botón «Responder» de la notificación: abre el selector de mensajes de esa cuenta. */
    private fun handleReplyIntent(i: Intent?) {
        // Respuesta escrita en la notificación con la app abierta: se envía directo, sin diálogo.
        val sendText = i?.getStringExtra(EXTRA_SEND_TEXT)
        val sendId = i?.getStringExtra(EXTRA_SELECT)
        if (!sendText.isNullOrEmpty() && sendId != null) {
            i.removeExtra(EXTRA_SEND_TEXT)
            accounts.firstOrNull { it.id == sendId }?.let { acc -> handler.postDelayed({ sendNow(acc, sendText) }, 500) }
            return
        }
        val id = i?.getStringExtra(EXTRA_REPLY) ?: return
        i.removeExtra(EXTRA_REPLY)
        val acc = accounts.firstOrNull { it.id == id } ?: return
        handler.postDelayed({ quickReply(acc) }, 500)
    }

    /** Elegir uno de los mensajes guardados (o escribir uno) y enviarlo ahora en el chat de la cuenta. */
    private fun quickReply(acc: Account) {
        val snips = store.snippets
        val labels = ArrayList<String>()
        snips.forEach { labels.add(it.take(70)) }
        labels.add("Texto propio…")
        MaterialAlertDialogBuilder(this)
            .setTitle("Responder en «${acc.name}»")
            .setItems(labels.toTypedArray()) { _, which ->
                if (which == labels.size - 1) {
                    val input = TextInputEditText(this)
                    input.minLines = 2
                    input.maxLines = 6
                    val holder = FrameLayout(this)
                    holder.setPadding(dp(20), dp(8), dp(20), 0)
                    holder.addView(input, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Mensaje para enviar ahora")
                        .setView(holder)
                        .setPositiveButton("Enviar") { _, _ -> sendNow(acc, input.text?.toString().orEmpty().trim()) }
                        .setNegativeButton("Cancelar", null)
                        .show()
                } else {
                    sendNow(acc, snips[which])
                }
            }
            .setNegativeButton("Ahora no", null)
            .show()
    }

    private fun sendNow(acc: Account, text: String) {
        if (text.isEmpty()) return
        acc.autoReply = text.take(4000)
        store.save(accounts)
        enqueueAutoReply(acc)
    }

    override fun onResume() {
        super.onResume()
        if (explorerOpen) explorer?.refresh() // por si volvió de darle el permiso de archivos
        webViews.values.forEach { it.onResume() }
        browser?.webView?.onResume()
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
        browser?.webView?.onPause()
        webViews.values.forEach {
            rememberPage(it, it.url)
            it.onPause()
        }
        super.onPause()
    }

    override fun onDestroy() {
        alive = false
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
        (browser?.webView ?: savedBrowserWebView)?.let { wv ->
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.destroy()
        }
        super.onDestroy()
    }

    // ---------------------------------------------------------------- Explorador de archivos

    private var explorer: FileExplorer? = null
    private var explorerOpen = false

    private val legacyStorageLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) {
            explorer?.refresh()
        }

    private val explorerHost = object : ExplorerHost {
        override fun surface(): Int = store.surface
        override fun onSurface(): Int = store.onSurface
        override fun accent(): Int = this@MainActivity.accent()
        override fun border(): Int =
            if (borderColor != 0) borderColor else ColorUtils.setAlphaComponent(store.onSurface, 60)
        override fun close() = setExplorerOpen(false)
        override fun message(text: String) = snack(text)
        override fun systemPicker() = switchToSystemPicker()
        override fun requestLegacyPermission() {
            legacyStorageLauncher.launch(
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            )
        }
    }

    /** Abre o cierra el explorador de archivos: un cajón grande, como el de ajustes, que sube desde la barra. */
    private fun setExplorerOpen(open: Boolean) {
        if (open == explorerOpen) return
        explorerOpen = open
        if (open) {
            closePanelsExcept("files")
            val ex = explorer ?: FileExplorer(this, explorerHost).also {
                explorer = it
                addSwipeHandle(it.view) { setExplorerOpen(false) }
                container.addView(
                    it.view,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.BOTTOM
                    ).apply { topMargin = dp(28) }
                )
            }
            setDockMerged(true)
            ex.view.animate().cancel()
            ex.view.visibility = View.VISIBLE
            ex.view.bringToFront()
            ex.open()
            ex.view.doOnLayout {
                it.translationY = it.height.toFloat()
                it.animate().translationY(0f).setDuration(260)
                    .setInterpolator(DecelerateInterpolator(1.6f)).start()
            }
        } else {
            explorer?.cancelPick() // si se cerró sin elegir, la página recibe «ningún archivo»
            val v = explorer?.view ?: return
            v.animate().cancel()
            v.animate().translationY(v.height.toFloat()).setDuration(200).withEndAction {
                if (!explorerOpen) {
                    v.visibility = View.GONE
                    if (!anyPanelOpen()) setDockMerged(false)
                }
            }.start()
        }
    }

    /** Si cambia el tema o la IA, los cajones se rearman con los colores nuevos (el navegador conserva su página). */
    private fun resetPanels() {
        explorer?.let { container.removeView(it.view); explorer = null }
        snippetsPanel?.let { container.removeView(it.view); snippetsPanel = null }
        browser?.let {
            savedBrowserWebView = it.detachWebView()
            container.removeView(it.view)
            browser = null
        }
        if (explorerOpen || snippetsOpen || browserOpen) {
            explorerOpen = false
            snippetsOpen = false
            browserOpen = false
            if (!drawerOpen) setDockMerged(false)
        }
    }

    // ---------------------------------------------------------------- Cajones: mensajes y navegador

    private var snippetsPanel: SnippetsPanel? = null
    private var snippetsOpen = false
    private var browser: BrowserPanel? = null
    private var browserOpen = false
    private var savedBrowserWebView: WebView? = null

    private fun anyPanelOpen(): Boolean = drawerOpen || explorerOpen || snippetsOpen || browserOpen

    /** Cajones grandes (archivos, mensajes, navegador): mientras estén abiertos la barra de cuentas no se pliega sola. */
    private fun subPanelOpen(): Boolean = explorerOpen || snippetsOpen || browserOpen

    private fun closePanelsExcept(keep: String) {
        if (keep != "settings") setDrawerOpen(false)
        if (keep != "files") setExplorerOpen(false)
        if (keep != "snippets") setSnippetsOpen(false)
        if (keep != "browser") setBrowserOpen(false)
    }

    /** ¿El toque cayó dentro de alguno de los cajones grandes abiertos? */
    private fun panelHit(ev: MotionEvent): Boolean =
        (explorerOpen && explorer?.view?.let { hit(it, ev) } == true) ||
            (snippetsOpen && snippetsPanel?.view?.let { hit(it, ev) } == true) ||
            (browserOpen && browser?.view?.let { hit(it, ev) } == true)

    /**
     * Franja en el borde superior de un cajón grande (con una rayita para agarrar): arrastrarla hacia abajo
     * lo sigue con el dedo y lo cierra si se suelta pasado cierto punto o con un envión; si no, vuelve a su lugar.
     */
    private fun addSwipeHandle(panel: LinearLayout, close: () -> Unit) {
        val handle = FrameLayout(this)
        val pill = View(this)
        pill.background = GradientDrawable().apply {
            cornerRadius = dp(3).toFloat()
            setColor(ColorUtils.setAlphaComponent(store.onSurface, 90))
        }
        handle.addView(pill, FrameLayout.LayoutParams(dp(40), dp(4), Gravity.CENTER))
        var startY = 0f
        var dragging = false
        var vt: android.view.VelocityTracker? = null
        handle.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startY = ev.rawY
                    dragging = true
                    panel.animate().cancel()
                    vt?.recycle()
                    vt = android.view.VelocityTracker.obtain().also { it.addMovement(ev) }
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> if (dragging) {
                    vt?.addMovement(ev)
                    panel.translationY = (ev.rawY - startY).coerceAtLeast(0f)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (dragging) {
                    dragging = false
                    vt?.addMovement(ev)
                    vt?.computeCurrentVelocity(1000)
                    val vy = vt?.yVelocity ?: 0f
                    vt?.recycle()
                    vt = null
                    val dy = panel.translationY
                    if (ev.actionMasked == MotionEvent.ACTION_UP && (dy > panel.height * 0.2f || (vy > 1200f && dy > dp(12)))) {
                        close()
                    } else {
                        panel.animate().translationY(0f).setDuration(180).start()
                    }
                }
            }
            true
        }
        panel.addView(handle, 0, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(20)))
    }

    private fun slideIn(v: View) {
        v.animate().cancel()
        v.visibility = View.VISIBLE
        v.bringToFront()
        v.doOnLayout {
            it.translationY = it.height.toFloat()
            it.animate().translationY(0f).setDuration(260).setInterpolator(DecelerateInterpolator(1.6f)).start()
        }
    }

    private fun slideOut(v: View, stillOpen: () -> Boolean) {
        v.animate().cancel()
        v.animate().translationY(v.height.toFloat()).setDuration(200).withEndAction {
            if (!stillOpen()) {
                v.visibility = View.GONE
                if (!anyPanelOpen()) setDockMerged(false)
                scheduleAutoHide()
            }
        }.start()
    }

    private val snippetsHost = object : SnippetsHost {
        override fun surface(): Int = store.surface
        override fun onSurface(): Int = store.onSurface
        override fun accent(): Int = this@MainActivity.accent()
        override fun border(): Int =
            if (borderColor != 0) borderColor else ColorUtils.setAlphaComponent(store.onSurface, 60)
        override fun close() = setSnippetsOpen(false)
        override fun message(text: String) = snack(text)
        override fun insert(text: String) = insertIntoPage(text)
    }

    private val browserHost = object : BrowserHost {
        override fun surface(): Int = store.surface
        override fun onSurface(): Int = store.onSurface
        override fun accent(): Int = this@MainActivity.accent()
        override fun border(): Int =
            if (borderColor != 0) borderColor else ColorUtils.setAlphaComponent(store.onSurface, 60)
        override fun close() = setBrowserOpen(false)
        override fun message(text: String) = snack(text)
        override fun multiProfile(): Boolean = multiProfileOk
        override fun chooseFile(callback: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams): Boolean {
            return chooseFiles(callback, params)
        }
        override fun download(wv: WebView, url: String, ua: String?, cd: String?, mime: String?) =
            browserDownload(wv, url, ua, cd, mime)
        override fun lastUrl(): String? = store.browserLastUrl
        override fun saveLastUrl(url: String) {
            store.browserLastUrl = url
        }
        override fun downloadSettings() = showBrowserDownloadSettings()
    }

    // ---------------------------------------------------------------- Descargas del navegador interno

    private fun hasAllFilesAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private fun askAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
            } catch (_: Exception) {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                } catch (_: Exception) {
                    snack("Activá el acceso a todos los archivos para AlternAIs en los ajustes del sistema")
                }
            }
        } else {
            storageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    /** Descarga desde el navegador según lo elegido: Descargas, preguntar cada vez o una carpeta fija. */
    private fun browserDownload(wv: WebView, url: String, ua: String?, cd: String?, mime: String?) {
        val web = url.startsWith("http://") || url.startsWith("https://")
        val mode = store.browserDownloadMode
        if (!web || mode == "default") {
            handleDownload(wv, url, ua, cd, mime)
            return
        }
        if (!hasAllFilesAccess()) {
            snack("Para elegir la carpeta hace falta el acceso a los archivos", "Dar permiso") { askAllFilesAccess() }
            return
        }
        val root = Environment.getExternalStorageDirectory()
        val fixed = store.browserDownloadDir?.let { File(it) }
        if (mode == "folder" && fixed != null && fixed.isDirectory) {
            saveToFolder(url, ua, cd, mime, fixed)
            return
        }
        val start = fixed ?: Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val name = URLUtil.guessFileName(url, cd, mime)
        FolderPicker.show(this, root, start, "Guardar «$name» en…") { dir ->
            store.browserDownloadDir = dir.path
            saveToFolder(url, ua, cd, mime, dir)
        }
    }

    /** Baja el archivo en un hilo propio (con las cookies del navegador) y lo deja en la carpeta elegida. */
    private fun saveToFolder(url: String, ua: String?, cd: String?, mime: String?, dir: File) {
        val name = URLUtil.guessFileName(url, cd, mime)
        snack("Descargando $name…")
        val cookie = try {
            if (multiProfileOk) ProfileStore.getInstance().getProfile(BrowserPanel.PROFILE)?.cookieManager?.getCookie(url)
            else android.webkit.CookieManager.getInstance().getCookie(url)
        } catch (_: Exception) {
            null
        }
        Thread {
            try {
                dir.mkdirs()
                val target = FileOps.uniqueFile(dir, name)
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 20000
                conn.readTimeout = 30000
                conn.instanceFollowRedirects = true
                if (!cookie.isNullOrEmpty()) conn.setRequestProperty("Cookie", cookie)
                if (!ua.isNullOrEmpty()) conn.setRequestProperty("User-Agent", ua)
                if (conn.responseCode !in 200..299) throw java.io.IOException("HTTP " + conn.responseCode)
                conn.inputStream.use { input -> target.outputStream().use { out -> input.copyTo(out) } }
                MediaScannerConnection.scanFile(this, arrayOf(target.path), null, null)
                runOnUiThread {
                    snack("Guardado en ${dir.name}: ${target.name}", "Abrir") {
                        try {
                            val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.files", target)
                            openDownloaded(uri, MimeTypeMap.getSingleton().getMimeTypeFromExtension(target.extension.lowercase()))
                        } catch (_: Exception) {
                            snack("No se pudo abrir el archivo")
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { snack("No se pudo descargar: " + (e.message ?: "")) }
            }
        }.start()
    }

    private fun showBrowserDownloadSettings() {
        val modes = arrayOf("default", "ask", "folder")
        val dir = store.browserDownloadDir
        val labels = arrayOf(
            "Carpeta Descargas (la de siempre)",
            "Preguntar dónde guardar cada vez",
            if (dir != null) "Siempre en: ${dir.removePrefix(Environment.getExternalStorageDirectory().path).ifEmpty { "/" }}"
            else "Siempre en una carpeta fija…"
        )
        MaterialAlertDialogBuilder(this)
            .setTitle("Dónde guarda el navegador")
            .setSingleChoiceItems(labels, modes.indexOf(store.browserDownloadMode).coerceAtLeast(0)) { d, which ->
                d.dismiss()
                when (modes[which]) {
                    "default" -> store.browserDownloadMode = "default"
                    "ask" -> {
                        store.browserDownloadMode = "ask"
                        if (!hasAllFilesAccess()) snack("Para elegir la carpeta hace falta el acceso a los archivos", "Dar permiso") { askAllFilesAccess() }
                    }
                    else -> {
                        if (!hasAllFilesAccess()) {
                            snack("Para elegir la carpeta hace falta el acceso a los archivos", "Dar permiso") { askAllFilesAccess() }
                        } else {
                            val root = Environment.getExternalStorageDirectory()
                            val start = dir?.let { File(it) }
                                ?: Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                            FolderPicker.show(this, root, start, "Carpeta fija para el navegador") { f ->
                                store.browserDownloadDir = f.path
                                store.browserDownloadMode = "folder"
                                snack("Se guardará en ${f.name}")
                            }
                        }
                    }
                }
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    /** Mensajes guardados: cajón con la lista; tocar uno lo escribe en el chat de la cuenta activa. */
    private fun showSnippets() = setSnippetsOpen(!snippetsOpen)

    private fun setSnippetsOpen(open: Boolean) {
        if (open == snippetsOpen) return
        if (open && currentId == null) {
            snack("Primero abrí una cuenta")
            return
        }
        snippetsOpen = open
        if (open) {
            closePanelsExcept("snippets")
            val p = snippetsPanel ?: SnippetsPanel(this, snippetsHost, snippetsVm).also {
                snippetsPanel = it
                addSwipeHandle(it.view) { setSnippetsOpen(false) }
                container.addView(
                    it.view,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, (container.height * 62) / 100, Gravity.BOTTOM
                    )
                )
            }
            setDockMerged(true)
            slideIn(p.view)
        } else {
            snippetsPanel?.view?.let { slideOut(it) { snippetsOpen } }
        }
    }

    /** Navegador web interno (universal, con su propia sesión): cajón casi a pantalla completa. */
    private fun setBrowserOpen(open: Boolean) {
        if (open == browserOpen) return
        browserOpen = open
        if (open) {
            closePanelsExcept("browser")
            val p = browser ?: BrowserPanel(this, browserHost, savedBrowserWebView).also {
                browser = it
                savedBrowserWebView = null
                addSwipeHandle(it.view) { setBrowserOpen(false) }
                container.addView(
                    it.view,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.BOTTOM
                    ).apply { topMargin = dp(28) }
                )
            }
            setDockMerged(true)
            p.open()
            slideIn(p.view)
        } else {
            browser?.view?.let { slideOut(it) { browserOpen } }
        }
    }

    /** Recarga la página de una cuenta mostrando el logo animado hasta que termine. */
    private fun reloadWithLogo(id: String) {
        val wv = webViews[id] ?: return
        failed.remove(id)
        loadingIds.add(id)
        handler.removeCallbacks(loadingSafety)
        handler.postDelayed(loadingSafety, 25000)
        updateErrorView()
        updateLoadingOverlay()
        wv.reload()
    }

    /** Muestra u oculta el logo en bucle según si la cuenta activa todavía está cargando. */
    private fun updateLoadingOverlay() {
        val want = currentId != null && loadingIds.contains(currentId) && !failed.contains(currentId)
        val v = loadingView
        if (want) {
            val view = v ?: SplashLogoView(this, loop = true).also {
                loadingView = it
                it.elevation = dp(2).toFloat()
                container.addView(it, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
            if (view.visibility != View.VISIBLE || view.alpha < 1f || loadingShownAt == 0L) {
                view.animate().cancel()
                view.alpha = 1f
                view.visibility = View.VISIBLE
                view.startLoop()
                loadingShownAt = System.currentTimeMillis()
            }
            view.bringToFront()
        } else if (v != null && v.visibility == View.VISIBLE && loadingShownAt != 0L) {
            // Que no parpadee si la carga es muy rápida: se queda al menos ~0,7 s.
            val wait = (700 - (System.currentTimeMillis() - loadingShownAt)).coerceAtLeast(0)
            loadingShownAt = 0L
            v.animate().cancel()
            v.animate().alpha(0f).setStartDelay(wait).setDuration(220).withEndAction {
                if (loadingShownAt == 0L) {
                    v.visibility = View.GONE
                    v.stopLoop()
                }
            }.start()
        }
    }

    /** Animación del logo al abrir la app: se dibuja encima de todo y se quita sola al terminar. */
    private fun showSplash() {
        val v = SplashLogoView(this)
        addContentView(v, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        v.elevation = dp(40).toFloat()
        v.onDone = { (v.parent as? ViewGroup)?.removeView(v) }
        v.start()
    }

    // ---------------------------------------------------------------- UI helpers

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun applySystemBars() {
        // Claude va oscuro y Replit claro, sin importar el tema del sistema.
        val light = !store.dark
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }

    /** Pinta la barra, el cajón y los fondos con el tema del servicio activo (Claude oscuro, Replit claro). */
    private fun applyServiceTheme() {
        window.statusBarColor = store.surface
        window.navigationBarColor = store.surface
        applySystemBars()
        findViewById<View>(R.id.rootLayout).setBackgroundColor(store.surface)
        if (::dockBg.isInitialized) dockBg.setColor(store.surface)
        if (::drawerBg.isInitialized) drawerBg.setColor(store.surface)
        if (::info.isInitialized) info.setTextColor(store.onSurface)
        drawerIcons.forEach { it.setTextColor(store.onSurface) }
        if (::emptyView.isInitialized) recolorTexts(emptyView, store.onSurface)
        if (::errorView.isInitialized) recolorTexts(errorView, store.onSurface)
        paintServiceTabs()
        resetPanels()
    }

    private fun recolorTexts(v: View, color: Int) {
        if (v is android.widget.Button) return
        if (v is TextView) v.setTextColor(color)
        if (v is ViewGroup) for (i in 0 until v.childCount) recolorTexts(v.getChildAt(i), color)
    }

    private fun dotDrawable(color: Int, selected: Boolean): GradientDrawable {
        val d = GradientDrawable()
        d.shape = GradientDrawable.OVAL
        d.setColor(color)
        if (selected) {
            d.setStroke(dp(3), onSurface())
        }
        return d
    }

    /** Texto e íconos neutros sobre la barra: dependen del servicio activo, no del tema del sistema. */
    private fun onSurface(): Int = store.onSurface

    /** Texto sobre los menús del sistema (hojas inferiores), que siguen el tema del sistema. */
    private fun sheetOnSurface(): Int =
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
                // Swipe hacia arriba despliega la barra de cuentas (como el cajón de un launcher); hacia abajo la pliega.
                if (abs(vy) > abs(vx) && abs(vy) > 500f) {
                    setDockCollapsed(vy > 0, true)
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
        // Plegada: la barra de tiempo ocupa el lugar del texto (punto a la izquierda, flecha a la derecha)
        info.visibility = if (collapsed) View.GONE else View.VISIBLE
        timeBar.visibility = if (collapsed) View.VISIBLE else View.GONE
        btnMore.visibility = if (collapsed) View.GONE else View.VISIBLE
        btnSnippets.visibility = if (collapsed) View.GONE else View.VISIBLE
        btnFiles.visibility = if (collapsed) View.GONE else View.VISIBLE
        btnBrowser.visibility = if (collapsed) View.GONE else View.VISIBLE
        if (collapsed) setDrawerOpen(false)
        btnToggleDock.animate().rotation(if (collapsed) 180f else 0f).setDuration(220).start()
        scheduleAutoHide()
    }

    /** Reinicia la cuenta regresiva para plegar la barra sola (si está desplegada). */
    private fun scheduleAutoHide() {
        handler.removeCallbacks(autoHide)
        if (!tutorialActive && !dockCollapsed && !subPanelOpen() && accounts.isNotEmpty()) handler.postDelayed(autoHide, AUTO_HIDE_MS)
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
            panelBorders(color)
            return
        }
        borderAnim = ValueAnimator.ofObject(ArgbEvaluator(), from, color).apply {
            duration = 260
            addUpdateListener {
                val c = it.animatedValue as Int
                dockBg.setStroke(dp(3), c)
                if (::drawerBg.isInitialized) drawerBg.setStroke(dp(3), c)
                panelBorders(c)
            }
            start()
        }
    }

    /** Los cajones (archivos, mensajes, navegador) llevan el mismo borde que la barra y los ajustes. */
    private fun panelBorders(c: Int) {
        explorer?.setBorder(c)
        snippetsPanel?.setBorder(c)
        browser?.setBorder(c)
    }

    private fun newTrayBg(): GradientDrawable {
        val fill = store.surface
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
    private fun styleTray(v: View, bg: GradientDrawable, radius: () -> Float): LayerDrawable {
        val layer = LayerDrawable(arrayOf(bg)).apply { setLayerInset(0, 0, 0, 0, -dp(8)) }
        v.background = layer
        v.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height + dp(24), radius())
            }
        }
        v.clipToOutline = true
        v.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ -> view.invalidateOutline() }
        return layer
    }

    /**
     * Con el cajón abierto la barra pierde las esquinas redondeadas y su borde de arriba, así el
     * borde de color del cajón sigue de corrido por los costados de la barra, sin hueco.
     */
    private fun setDockMerged(merged: Boolean) {
        val r = if (merged) 0f else dp(22).toFloat()
        dockRadius = r
        dockBg.cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        // El borde de arriba se mantiene también con un cajón abierto: es el separador entre el cajón y la barra.
        dockLayer.setLayerInset(0, 0, 0, 0, -dp(8))
        dock.invalidateOutline()
        dock.invalidate()
    }

    private fun brand(): Int = ContextCompat.getColor(this, R.color.brand)

    /** Color de la cuenta activa: lo usan los íconos y detalles de la barra y del cajón. */
    private fun accent(): Int = chrome(accounts.firstOrNull { it.id == currentId })

    /** Color de la interfaz (borde, íconos, punto): el de la cuenta activa, en Claude y en Replit. */
    private fun chrome(acc: Account?): Int = acc?.color ?: brand()

    private fun retintIcons() {
        val c = accent()
        btnMore.imageTintList = ColorStateList.valueOf(c)
        btnSnippets.imageTintList = ColorStateList.valueOf(c)
        btnFiles.imageTintList = ColorStateList.valueOf(c)
        btnBrowser.imageTintList = ColorStateList.valueOf(c)
        btnToggleDock.imageTintList = ColorStateList.valueOf(c)
        dockHandle.background = GradientDrawable().apply {
            cornerRadius = dp(2).toFloat()
            setColor(ColorUtils.setAlphaComponent(c, 150))
        }
        drawerCaption?.setTextColor(c)
        paintServiceTabs()
        settingsVm.setAccent(c)
        drawerIcons.forEach { tv -> tv.compoundDrawablesRelative[0]?.setTint(c) }
    }

    private fun applyIcon(tv: TextView, icon: Int, tint: Int = accent()) {
        val dr = AppCompatResources.getDrawable(this, icon)!!.mutate()
        dr.setTint(tint)
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
        styleTray(d, drawerBg) { dp(22).toFloat() }

        // El contenido del cajón (pestañas Claude/Replit, logo, interruptor de burbuja y filas) es Compose.
        val actions = object : SettingsActions {
            override fun selectService(service: AiService) = switchService(Service.fromKey(service.key))
            override fun toggleTheme() = this@MainActivity.toggleTheme()
            override fun toggleBubble(turnOn: Boolean) {
                setDrawerOpen(false)
                setBubbleEnabled(turnOn)
            }
            override fun reloadCurrent() {
                setDrawerOpen(false)
                currentId?.let { reloadWithLogo(it) }
            }
            override fun openDownloads() {
                setDrawerOpen(false)
                try {
                    startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
                } catch (_: Exception) {
                    snack("No se pudo abrir Descargas")
                }
            }
            override fun exportAccounts() {
                setDrawerOpen(false)
                exportLauncher.launch("AlternAIs-cuentas.json")
            }
            override fun importAccounts() {
                setDrawerOpen(false)
                importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            }
            override fun showAbout() {
                setDrawerOpen(false)
                showAboutDialog()
            }
        }
        settingsVm.setAccent(accent())
        d.addView(
            createSettingsView(this, settingsVm, actions),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        d.visibility = View.GONE
        container.addView(
            d,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM
            )
        )
        drawer = d
    }

    /** Resalta la mitad activa (Claude / Replit) con el color de la cuenta activa. */
    private fun paintServiceTabs() {
        val c = accent()
        val active = store.service
        listOf(tabClaude to Service.CLAUDE, tabReplit to Service.REPLIT).forEach { (tv, svc) ->
            tv ?: return@forEach
            val on = svc == active
            tv.setTextColor(if (on) c else (onSurface() and 0x00FFFFFF) or (0x99 shl 24))
            tv.background = if (on) GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor((c and 0x00FFFFFF) or (0x2E shl 24))
            } else null
        }
    }

    /**
     * Alterna entre vista oscura y clara del servicio activo. La interfaz se repinta al instante; las
     * páginas fijan su tema al crearse, así que se recrean y recargan (la sesión vive en el perfil).
     */
    private fun toggleTheme() {
        store.dark = !store.dark
        applyServiceTheme()
        rebuildChips()
        updateInfo()
        retintIcons()
        val keep = currentId
        accounts.forEach { acc ->
            webViews.remove(acc.id)?.let { wv ->
                rememberPage(wv, wv.url)
                (wv.parent as? ViewGroup)?.removeView(wv)
                wv.destroy()
            }
            failed.remove(acc.id)
        }
        if (keep != null) select(keep)
        updateErrorView()
        BubbleService.refresh()
        scheduleAutoHide()
    }

    /**
     * Cambia de mitad de la app (Claude ↔ Replit). Cada una tiene sus cuentas, su cuenta activa y sus
     * perfiles de WebView; los WebViews de la otra mitad quedan ocultos y conservan su sesión.
     */
    private fun switchService(target: Service) {
        setDrawerOpen(false)
        if (target == store.service) return
        webViews.values.forEach { rememberPage(it, it.url) }
        store.service = target
        applyServiceTheme()
        accounts.clear()
        accounts.addAll(store.load())
        currentId = null
        previousId = null
        rebuildChips(true)
        val start = store.selectedId?.takeIf { id -> accounts.any { it.id == id } }
            ?: accounts.firstOrNull()?.id
        if (start != null) {
            select(start)
        } else {
            webViews.values.forEach {
                it.animate().cancel()
                it.visibility = View.GONE
            }
            progress.visibility = View.INVISIBLE
            setBorderColor(chrome(null))
            updateInfo()
            updateErrorView()
        }
        updateEmptyState()
        updateShortcuts()
        BubbleService.refresh()
        retintIcons()
        scheduleAutoHide()
    }

    /** El cajón sale desde detrás de la barra (el contenedor recorta lo que queda por debajo). */
    private fun setDrawerOpen(open: Boolean) {
        if (open == drawerOpen) return
        drawerOpen = open
        drawer.animate().cancel()
        if (open) {
            closePanelsExcept("settings")
            settingsVm.setBubbleShown(store.bubbleEnabled && BubbleService.canDraw(this))
            setDockMerged(true)
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
                .withEndAction {
                    if (!drawerOpen) {
                        drawer.visibility = View.GONE
                        if (!anyPanelOpen()) setDockMerged(false)
                    }
                }.start()
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
        if (!tutorialActive && ev.actionMasked == MotionEvent.ACTION_DOWN && ::dock.isInitialized && ::drawer.isInitialized &&
            !dockCollapsed && accounts.isNotEmpty() && dock.visibility == View.VISIBLE &&
            !hit(dock, ev) && !(drawerOpen && hit(drawer, ev)) &&
            !subPanelOpen() && !(panelHit(ev))
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
            private var downX = 0f
            private var downY = 0f
            private var dragging = false
            private val slop = android.view.ViewConfiguration.get(context).scaledTouchSlop

            override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                scheduleAutoHide()
                return super.dispatchTouchEvent(ev)
            }

            // Un arrastre vertical sobre cualquier parte de la barra: arriba la despliega, abajo la pliega.
            override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = ev.rawX
                        downY = ev.rawY
                        dragging = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dy = ev.rawY - downY
                        if (!dragging && abs(dy) > slop * 2 && abs(dy) > abs(ev.rawX - downX) * 1.5f) dragging = true
                    }
                }
                return dragging
            }

            override fun onTouchEvent(ev: MotionEvent): Boolean {
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = ev.rawX
                        downY = ev.rawY
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val dy = ev.rawY - downY
                        if (dragging && abs(dy) > dp(28)) setDockCollapsed(dy > 0, true)
                        dragging = false
                    }
                    MotionEvent.ACTION_CANCEL -> dragging = false
                }
                return true
            }
        }
        d.id = R.id.dock
        d.orientation = LinearLayout.VERTICAL
        d.elevation = elev

        // Fondo con esquinas superiores redondeadas y borde de color
        borderColor = 0
        dockBg = newTrayBg()
        dockRadius = dp(22).toFloat()
        dockLayer = styleTray(d, dockBg) { dockRadius }

        // Manija: deja ver que la barra se despliega con un swipe hacia arriba
        val handleBar = View(this)
        dockHandle = handleBar
        d.addView(handleBar, LinearLayout.LayoutParams(dp(36), dp(4)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(7)
        })

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

        val bar = TimeBar(this)
        bar.visibility = View.GONE
        row.addView(bar, LinearLayout.LayoutParams(0, dp(22), 1f).apply { marginEnd = dp(8) })

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
        val web = iconButton(R.id.btnBrowser, R.drawable.ic_globe, "Navegador")
        row.addView(web, LinearLayout.LayoutParams(dp(42), dp(42)))
        val files = iconButton(R.id.btnFiles, R.drawable.ic_folder, "Archivos")
        row.addView(files, LinearLayout.LayoutParams(dp(42), dp(42)))
        val snip = iconButton(R.id.btnSnippets, R.drawable.ic_message, "Mensajes guardados")
        row.addView(snip, LinearLayout.LayoutParams(dp(42), dp(42)))
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
        timeBar = bar
        info = name
        btnMore = more
        btnSnippets = snip
        btnFiles = files
        btnBrowser = web
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

    private val circleSize get() = dp(44)
    private val circleExtra get() = dp(7)

    private fun rebuildChips(animateIn: Boolean = false) {
        chips.clear()
        chipGroup.removeAllViews()
        val gap = LinearLayout.LayoutParams(circleSize, circleSize + circleExtra * 2).apply {
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
        // mismo diámetro que el disco de color de los círculos (el resto es anillo y aire)
        val addSize = circleSize - dp(10)
        chipGroup.addView(add, LinearLayout.LayoutParams(addSize, addSize).apply {
            marginStart = dp(11)
            marginEnd = dp(11)
        })

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

    /**
     * Redibuja cada círculo: color e inicial; si la cuenta descansa, el anillo es el tiempo que falta
     * (se va borrando); la cuenta activa lleva una rayita debajo.
     */
    private fun refreshCircles() {
        accounts.forEach { acc ->
            val iv = chips[acc.id] ?: return@forEach
            iv.setImageBitmap(
                Ui.timerAvatar(
                    acc.color, Ui.initial(acc.name), circleSize, circleExtra,
                    acc.limitFraction(), onSurface(), acc.id == currentId, onSurface()
                )
            )
            iv.contentDescription = if (acc.limitActive()) {
                "${acc.name}, en descanso, faltan " + formatRemaining(acc.limitUntil - System.currentTimeMillis())
            } else acc.name
        }
    }

    private fun refreshChipTexts() = refreshCircles()

    private fun updateSelection() = refreshCircles()

    private fun updateInfo() {
        val acc = accounts.firstOrNull { it.id == currentId }
        if (acc == null) {
            info.text = ""
            timeBar.set(0f, "", Color.GRAY, onSurface())
            return
        }
        setBorderColor(chrome(acc))
        retintIcons()
        val dot = GradientDrawable()
        dot.shape = GradientDrawable.OVAL
        dot.setColor(chrome(acc))
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

        if (acc.limitActive()) {
            val left = formatRemaining(acc.limitUntil - System.currentTimeMillis())
            timeBar.set(acc.limitFraction(), "${acc.name} · faltan $left", chrome(acc), onSurface())
        } else {
            timeBar.set(1f, "${acc.name} · Disponible", chrome(acc), onSurface())
        }
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        var changed = false
        accounts.forEach { a ->
            if (a.limitUntil in 1L..now) {
                a.limitUntil = 0L
                a.limitStart = 0L
                changed = true
                if (a.autoReply.isEmpty()) snack("«${a.name}» ya puede usarse de nuevo", "Responder") { quickReply(a) }
                else Toast.makeText(this, "«${a.name}» ya puede usarse de nuevo", Toast.LENGTH_LONG).show()
            }
        }
        if (changed) {
            store.save(accounts)
            BubbleService.refresh()
        }
        // Descanso terminado con respuesta automática pendiente (también si la app estaba cerrada cuando venció).
        accounts.filter { it.autoReply.isNotEmpty() && !it.limitActive(now) }.forEach { enqueueAutoReply(it) }
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
        progress.setIndicatorColor(chrome(acc))
        progress.visibility = View.INVISIBLE
        updateSelection()
        updateInfo()
        updateErrorView()
        updateLoadingOverlay()
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

    private fun rememberPage(view: WebView?, url: String?) {
        val id = view?.tag as? String ?: return
        val safeUrl = urlForRestore(store.service, url) ?: return
        val acc = accounts.firstOrNull { it.id == id } ?: return
        if (acc.lastUrl == safeUrl) return
        acc.lastUrl = safeUrl
        store.save(accounts)
    }

    private fun retryCurrent() {
        val id = currentId ?: return
        failed.remove(id)
        updateErrorView()
        reloadWithLogo(id)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(acc: Account): WebView {
        // Claude oscuro, Replit claro: el WebView toma el tema de su contexto (prefers-color-scheme).
        val dark = store.dark
        val wv = WebView(ContextThemeWrapper(this, if (dark) R.style.Theme_Claudeck_WebDark else R.style.Theme_Claudeck_WebLight))
        wv.tag = acc.id
        loadingIds.add(acc.id)
        handler.removeCallbacks(loadingSafety)
        handler.postDelayed(loadingSafety, 25000)
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
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(wv.settings, dark)
            } else if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                @Suppress("DEPRECATION")
                WebSettingsCompat.setForceDark(
                    wv.settings,
                    if (dark) WebSettingsCompat.FORCE_DARK_ON else WebSettingsCompat.FORCE_DARK_OFF
                )
                if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)) {
                    @Suppress("DEPRECATION")
                    WebSettingsCompat.setForceDarkStrategy(
                        wv.settings, WebSettingsCompat.DARK_STRATEGY_PREFER_WEB_THEME_OVER_USER_AGENT_DARKENING
                    )
                }
            }
        } catch (_: Exception) {
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
                rememberPage(view, url)
                val id = view?.tag as? String ?: return
                if (failed.remove(id) && id == currentId) updateErrorView()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                view?.evaluateJavascript(HOOK_JS, null)
                rememberPage(view, url)
                (view?.tag as? String)?.let { if (loadingIds.remove(it)) updateLoadingOverlay() }
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                rememberPage(view, url)
            }

            override fun onReceivedError(
                view: WebView?, request: WebResourceRequest?, error: WebResourceError?
            ) {
                if (request?.isForMainFrame != true) return
                val id = view?.tag as? String ?: return
                failed.add(id)
                if (loadingIds.remove(id)) updateLoadingOverlay()
                if (id == currentId) updateErrorView()
            }
        }
        wv.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: android.webkit.PermissionRequest?) {
                if (request != null) handleWebPermission(request)
            }

            override fun onPermissionRequestCanceled(request: android.webkit.PermissionRequest?) {
                if (request != null && request === pendingMic) pendingMic = null
            }

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
                return chooseFiles(filePathCallback, fileChooserParams)
            }
        }
        wv.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            handleDownload(wv, url, userAgent, contentDisposition, mimeType)
        }
        container.addView(wv, 0)
        wv.loadUrl(urlForRestore(store.service, acc.lastUrl) ?: store.service.startUrl)
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
            req.setDescription("AlternAIs")
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
        val inEmail = v.findViewById<TextInputEditText>(R.id.inputEmail)
        val row = v.findViewById<LinearLayout>(R.id.colorRow)
        var color = existing?.color ?: PALETTE[accounts.size % PALETTE.size]
        inName.setText(existing?.name ?: "")
        inPurpose.setText(existing?.purpose ?: "")
        inEmail.setText(existing?.email ?: "")

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
                val email = cleanEmail(inEmail.text?.toString())
                if (existing == null) {
                    val acc = Account(Account.newId(), name, purpose, color, email = email)
                    accounts.add(acc)
                    onAccountsChanged()
                    rebuildChips()
                    updateEmptyState()
                    select(acc.id)
                    maybeSuggestBubble()
                } else {
                    existing.name = name
                    existing.purpose = purpose
                    existing.email = email
                    existing.color = color
                    onAccountsChanged()
                    rebuildChips()
                    updateInfo()
                    if (existing.id == currentId) progress.setIndicatorColor(chrome(existing))
                }
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun cleanEmail(raw: String?): String {
        val t = raw?.trim().orEmpty().take(80)
        return if (Regex("^[^\\s@]+@[^\\s@]+$").matches(t)) t else ""
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
        t.setTextColor(sheetOnSurface())
        texts.addView(t)
        if (!subtitle.isNullOrBlank()) {
            val s = TextView(this)
            s.text = subtitle
            s.textSize = 13f
            s.setTextColor(ColorUtils.setAlphaComponent(sheetOnSurface(), 150))
            texts.addView(s)
        }
        head.addView(texts)
        col.addView(head)
    }

    private fun sheetRow(col: LinearLayout, sheet: BottomSheetDialog?, text: String, icon: Int? = null, tint: Int = accent(), onClick: () -> Unit): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.textSize = 16f
        tv.setTextColor(if (sheet == null) onSurface() else sheetOnSurface())
        tv.gravity = Gravity.CENTER_VERTICAL
        tv.setPadding(dp(16), dp(14), dp(16), dp(14))
        if (icon != null) applyIcon(tv, icon, tint)
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
        sheetHeader(col, acc.name, listOf(acc.purpose, acc.email, status).filter { it.isNotBlank() }.joinToString(" · "), acc.color)
        sheetRow(col, sheet, "Editar nombre, propósito o color", R.drawable.ic_edit, chrome(acc)) { showAccountDialog(acc) }
        sheetRow(col, sheet, "Recargar", R.drawable.ic_reload, chrome(acc)) { if (webViews.containsKey(acc.id)) reloadWithLogo(acc.id) else select(acc.id) }
        sheetRow(col, sheet, "Marcar límite alcanzado…", R.drawable.ic_clock, chrome(acc)) { showLimitDialog(acc) }
        if (acc.limitActive()) sheetRow(col, sheet, "Quitar marca de límite", R.drawable.ic_check, chrome(acc)) { setLimit(acc, 0L) }
        sheetRow(col, sheet, "Cerrar sesión y borrar datos", R.drawable.ic_logout, chrome(acc)) {
            confirm(
                "Cerrar sesión",
                "Se cierra la sesión de «${acc.name}» y se borran sus cookies y datos en este dispositivo."
            ) { wipeAccount(acc, remove = false) }
        }
        sheetRow(col, sheet, "Eliminar cuenta", R.drawable.ic_trash, chrome(acc)) {
            confirm(
                "Eliminar cuenta",
                "Se quita «${acc.name}» de la app y se borran sus datos en este dispositivo. Tu cuenta de ${store.service.label} no se toca."
            ) { wipeAccount(acc, remove = true) }
        }
        sheet.setContentView(col)
        sheet.show()
    }

    // ---------------------------------------------------------------- Mensajes guardados

    /**
     * Escribe `text` en la caja de chat de la página activa: en el campo que tenga el foco o, si no hay
     * ninguno, en la última caja de texto visible. Si no encuentra dónde, lo copia al portapapeles.
     */
    private fun insertIntoPage(text: String) {
        val wv = currentId?.let { webViews[it] } ?: return
        val js = """
            (function(t){
              function editable(e){return e&&(e.isContentEditable||e.tagName==='TEXTAREA'||(e.tagName==='INPUT'&&/^(text|search|)$/.test(e.type)));}
              var el=document.activeElement;
              if(!editable(el)){
                var c=[].slice.call(document.querySelectorAll('div[contenteditable="true"],textarea')).filter(function(x){return x.offsetParent!==null;});
                el=c.length?c[c.length-1]:null;
                if(!el) return 'none';
                el.focus();
                if(el.isContentEditable){var s=window.getSelection();s.selectAllChildren(el);s.collapseToEnd();}
              }
              if(el.isContentEditable){
                if(!document.execCommand('insertText',false,t)) return 'none';
              } else {
                var proto=el.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;
                var set=Object.getOwnPropertyDescriptor(proto,'value').set;
                var v=el.value||''; var a=el.selectionStart==null?v.length:el.selectionStart; var b=el.selectionEnd==null?v.length:el.selectionEnd;
                set.call(el,v.slice(0,a)+t+v.slice(b));
                el.selectionStart=el.selectionEnd=a+t.length;
                el.dispatchEvent(new Event('input',{bubbles:true}));
              }
              return 'ok';
            })(${org.json.JSONObject.quote(text)})
        """.trimIndent()
        wv.requestFocus()
        wv.evaluateJavascript(js) { res ->
            if (res?.contains("ok") != true) {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("AlternAIs", text))
                snack("No encontré la caja de chat: copié el mensaje, pegalo donde quieras")
            }
        }
    }

    private fun showAboutDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Acerca de AlternAIs")
            .setMessage(
                "AlternAIs organiza sesiones independientes de claude.ai y replit.com (cada uno con sus propias cuentas) para que puedas cambiar de cuenta sin mezclar sus datos.\n\n" +
                    "Pensada y diseñada por Andrés Londero.\n\n" +
                    "No es una aplicación oficial de Anthropic. Si el acceso con Google falla en el navegador integrado, iniciá sesión con tu correo electrónico."
            )
            .setPositiveButton("Listo", null)
            .show()
    }

    // ---------------------------------------------------------------- Tutorial de primera ejecución

    private fun startTutorial() {
        if (tutorialActive || store.tutorialShown || accounts.isNotEmpty()) return
        store.tutorialShown = true
        tutorialActive = true
        handler.removeCallbacks(autoHide)
        setDockCollapsed(false, false)

        val host = findViewById<FrameLayout>(android.R.id.content)
        val overlay = TutorialOverlay(this)
        tutorialOverlay = overlay
        host.addView(
            overlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        showTutorialStep(0)
    }

    private fun showTutorialStep(index: Int) {
        if (!tutorialActive) return
        tutorialIndex = index
        val overlay = tutorialOverlay ?: return
        val title: String
        val body: String
        val target: View?
        when (index) {
            0 -> {
                title = "Bienvenido a AlternAIs"
                body = "Cada cuenta conserva su propia sesión. Cuando Claude te pida iniciar sesión, elegí tu correo electrónico y el código que te envía; «Continuar con Google» puede fallar en un navegador integrado."
                target = emptyView
            }
            1 -> {
                title = "Agregá tus cuentas"
                body = "Tocá el + para crear una sesión independiente. El tutorial no agrega cuentas ni guarda datos de ejemplo."
                target = chipGroup.getChildAt(chipGroup.childCount - 1)
            }
            2 -> {
                title = "Cambiá y administrá sesiones"
                body = "Elegí una cuenta tocando su círculo. Mantenelo apretado para abrir sus opciones: recargar, marcar un límite o borrar los datos de esa sesión."
                target = chipScroll
            }
            3 -> {
                title = "Ajustes"
                body = "Abrí el cajón de ajustes para activar la burbuja, recargar la cuenta o ver Descargas. Arriba podés pasar de Claude a Replit y volver: cada uno tiene sus propias cuentas. Lo abrí para mostrarte dónde está."
                setDockCollapsed(false, false)
                setDrawerOpen(true)
                target = drawer
            }
            else -> {
                title = "Acerca de"
                body = "Acerca de AlternAIs muestra qué hace la app y quién la pensó y diseñó. La opción está en este menú."
                setDrawerOpen(true)
                target = drawer // «Acerca de» ahora vive dentro de Compose: se resalta todo el cajón
            }
        }
        overlay.setStep(index + 1, 5, title, body, target)
        if (index >= 3) {
            handler.postDelayed({ if (tutorialActive) overlay.refreshTarget(target) }, 300)
        }
    }

    private fun advanceTutorial() {
        if (tutorialIndex >= 4) finishTutorial()
        else showTutorialStep(tutorialIndex + 1)
    }

    private fun finishTutorial() {
        if (!tutorialActive) return
        tutorialActive = false
        store.tutorialShown = true
        handler.removeCallbacks(autoHide)
        val overlay = tutorialOverlay
        tutorialOverlay = null
        (overlay?.parent as? ViewGroup)?.removeView(overlay)
        setDrawerOpen(false)
        scheduleAutoHide()
    }

    private inner class TutorialOverlay(context: Context) : FrameLayout(context) {
        private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(184, 17, 14, 12)
        }
        private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(2).toFloat()
        }
        private val card: MaterialCardView
        private val progress: TextView
        private val heading: TextView
        private val message: TextView
        private val nextButton: MaterialButton
        private var target: View? = null

        init {
            setWillNotDraw(false)
            isClickable = true
            isFocusable = true

            card = MaterialCardView(this@MainActivity).apply {
                radius = dp(22).toFloat()
                cardElevation = dp(14).toFloat()
                strokeWidth = dp(1)
                strokeColor = ColorUtils.setAlphaComponent(onSurface(), 38)
                setCardBackgroundColor(store.surface)
                useCompatPadding = true
            }
            val content = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(18), dp(20), dp(16))
            }
            progress = TextView(this@MainActivity).apply {
                textSize = 11f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(accent())
            }
            content.addView(progress)
            heading = TextView(this@MainActivity).apply {
                textSize = 20f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(onSurface())
                setPadding(0, dp(6), 0, dp(6))
            }
            content.addView(heading)
            message = TextView(this@MainActivity).apply {
                textSize = 14.5f
                setTextColor(onSurface())
                setLineSpacing(dp(3).toFloat(), 1f)
            }
            content.addView(message)

            val actions = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(14), 0, 0)
            }
            val skip = TextView(this@MainActivity).apply {
                text = "Saltar tutorial"
                textSize = 13f
                setTextColor(ColorUtils.setAlphaComponent(onSurface(), 190))
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(10), dp(10), dp(10))
                setBackgroundResource(selectableBg())
                isClickable = true
                isFocusable = true
                setOnClickListener { finishTutorial() }
            }
            actions.addView(skip)
            val spacer = View(this@MainActivity)
            actions.addView(spacer, LinearLayout.LayoutParams(0, 1, 1f))
            nextButton = MaterialButton(this@MainActivity).apply {
                text = "Siguiente"
                setOnClickListener { advanceTutorial() }
            }
            actions.addView(nextButton)
            content.addView(actions)
            card.addView(content)

            val cardWidth = minOf(dp(420), resources.displayMetrics.widthPixels - dp(32))
            addView(
                card,
                FrameLayout.LayoutParams(cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    leftMargin = dp(16)
                    rightMargin = dp(16)
                }
            )
        }

        fun setStep(number: Int, total: Int, title: String, body: String, focus: View?) {
            progress.text = "TUTORIAL · $number DE $total"
            heading.text = title
            message.text = body
            nextButton.text = if (number == total) "Terminar" else "Siguiente"
            refreshTarget(focus)
        }

        fun refreshTarget(focus: View?) {
            target = focus?.takeIf { it.isShown }
            invalidate()
            post { positionCard() }
        }

        private fun focusRect(): RectF? {
            val view = target ?: return null
            if (!view.isShown || width == 0 || height == 0) return null
            val targetPos = IntArray(2)
            val overlayPos = IntArray(2)
            view.getLocationOnScreen(targetPos)
            getLocationOnScreen(overlayPos)
            val pad = dp(7).toFloat()
            val r = RectF(
                targetPos[0] - overlayPos[0] - pad,
                targetPos[1] - overlayPos[1] - pad,
                targetPos[0] - overlayPos[0] + view.width + pad,
                targetPos[1] - overlayPos[1] + view.height + pad
            )
            return r.takeIf { it.right > 0 && it.bottom > 0 && it.left < width && it.top < height }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val r = focusRect()
            if (r == null) {
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
            } else {
                canvas.drawRect(0f, 0f, width.toFloat(), r.top, dimPaint)
                canvas.drawRect(0f, r.bottom, width.toFloat(), height.toFloat(), dimPaint)
                canvas.drawRect(0f, r.top, r.left, r.bottom, dimPaint)
                canvas.drawRect(r.right, r.top, width.toFloat(), r.bottom, dimPaint)
                outlinePaint.color = accent()
                canvas.drawRoundRect(r, dp(14).toFloat(), dp(14).toFloat(), outlinePaint)
            }
        }

        private fun positionCard() {
            if (width == 0 || height == 0) return
            val lp = card.layoutParams as? FrameLayout.LayoutParams ?: return
            val maxWidth = (width - dp(32)).coerceAtLeast(dp(240))
            lp.width = minOf(dp(420), maxWidth)
            card.layoutParams = lp
            card.measure(
                MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST)
            )
            val cardHeight = card.measuredHeight
            val focus = focusRect()
            val below = focus?.bottom?.plus(dp(16)) ?: dp(24).toFloat()
            val top = if (below + cardHeight <= height - dp(16)) {
                below
            } else {
                maxOf(dp(16).toFloat(), (focus?.top ?: height.toFloat()) - cardHeight - dp(16))
            }
            lp.topMargin = top.toInt()
            card.layoutParams = lp
        }
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
                        "para AlternAIs en la pantalla que se abre y volvé."
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

    // ---------------------------------------------------------------- Exportar / importar cuentas

    /**
     * El archivo lleva solo nombre, propósito, color y marcas de límite de cada cuenta (Claude y
     * Replit). NO lleva sesiones ni contraseñas: al importar hay que iniciar sesión en cada cuenta.
     */
    private fun writeExport(uri: Uri) {
        try {
            val root = JSONObject().put("app", "AlternAIs").put("version", 1)
                .put("exportedAt", System.currentTimeMillis())
            val services = JSONObject()
            var total = 0
            Service.values().forEach { svc ->
                val arr = JSONArray()
                store.loadFor(svc).forEach { a ->
                    total++
                    arr.put(
                        JSONObject().put("name", a.name).put("purpose", a.purpose).put("email", a.email)
                            .put("color", String.format("#%06X", a.color and 0xFFFFFF))
                            .put("limitUntil", a.limitUntil).put("limitStart", a.limitStart)
                            .put("lastUrl", urlForRestore(svc, a.lastUrl) ?: "")
                            .put("selected", a.id == store.selectedIdFor(svc))
                    )
                }
                services.put(svc.key, JSONObject().put("accounts", arr))
            }
            root.put("services", services)
            contentResolver.openOutputStream(uri, "wt")?.use { it.write(root.toString(2).toByteArray()) }
                ?: throw IllegalStateException("sin salida")
            MaterialAlertDialogBuilder(this)
                .setTitle("Cuentas exportadas")
                .setMessage("Se guardaron $total cuentas. El archivo no incluye las sesiones: al importarlo en otro dispositivo vas a tener que iniciar sesión en cada cuenta.")
                .setPositiveButton("Entendido", null)
                .show()
        } catch (e: Exception) {
            snack("No se pudo exportar")
        }
    }

    private fun readImport(uri: Uri) {
        try {
            val text = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?.takeIf { it.size <= 1_000_000 }?.toString(Charsets.UTF_8)
                ?: throw IllegalStateException("archivo vacío o muy grande")
            val services = JSONObject(text).getJSONObject("services")
            val now = System.currentTimeMillis()
            var added = 0
            var skipped = 0
            Service.values().forEach { svc ->
                val arr = services.optJSONObject(svc.key)?.optJSONArray("accounts") ?: return@forEach
                val list = store.loadFor(svc).toMutableList()
                for (i in 0 until minOf(arr.length(), 50)) {
                    val o = arr.optJSONObject(i) ?: continue
                    val name = o.optString("name").trim().take(24)
                    if (name.isEmpty() || list.any { it.name.equals(name, ignoreCase = true) }) {
                        skipped++
                        continue
                    }
                    val color = try {
                        Color.parseColor(o.optString("color"))
                    } catch (_: Exception) {
                        PALETTE[list.size % PALETTE.size]
                    }
                    val until = o.optLong("limitUntil", 0L)
                    val active = until > now
                    val imported = Account(
                        id = Account.newId(), name = name,
                        purpose = o.optString("purpose").trim().take(60), color = color,
                        limitUntil = if (active) until else 0L,
                        limitStart = if (active) o.optLong("limitStart", 0L) else 0L,
                        lastUrl = urlForRestore(svc, o.optString("lastUrl")) ?: "",
                        email = cleanEmail(o.optString("email"))
                    )
                    // Si la mitad no tenía cuenta activa, queda activa la que estaba marcada en el archivo.
                    if (store.selectedIdFor(svc) == null && (o.optBoolean("selected") || list.isEmpty())) {
                        store.setSelectedFor(svc, imported.id)
                    }
                    list.add(imported)
                    added++
                }
                store.saveFor(svc, list)
            }
            accounts.clear()
            accounts.addAll(store.load())
            rebuildChips()
            updateEmptyState()
            updateShortcuts()
            BubbleService.refresh()
            if (currentId == null && accounts.isNotEmpty()) select(accounts.first().id)
            MaterialAlertDialogBuilder(this)
                .setTitle("Cuentas importadas")
                .setMessage(
                    "Se agregaron $added cuentas" + (if (skipped > 0) " ($skipped ya existían o no eran válidas)" else "") +
                        ". Iniciá sesión en cada una: las sesiones no se copian entre dispositivos."
                )
                .setPositiveButton("Entendido", null)
                .show()
        } catch (e: Exception) {
            snack("El archivo no es válido")
        }
    }

    // ---------------------------------------------------------------- Límites de uso

    /** Opciones según la cuota de cada IA (reglas en `LimitOptions`); los diálogos son Compose (`ui/limits`). */
    private fun showLimitDialog(acc: Account) {
        ensureLimitHost()
        limitVm.openOptions(acc.id, AiService.fromKey(store.service.key))
    }

    /** Agrega (una sola vez) la vista que aloja los diálogos de límites en Compose. */
    private fun ensureLimitHost() {
        if (limitHost != null) return
        limitHost = createLimitHost(this, limitVm, limitActions).also {
            addContentView(it, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private val limitActions = object : LimitActions {
        override fun apply(accountId: String, untilMs: Long) {
            accounts.firstOrNull { it.id == accountId }?.let { setLimit(it, untilMs) }
        }
        override fun pickRenewalDate(accountId: String) {
            accounts.firstOrNull { it.id == accountId }?.let { this@MainActivity.pickRenewalDate(it) }
        }
        override fun message(text: String) = snack(text)
        override fun surface(): Int = store.surface
        override fun onSurface(): Int = store.onSurface
        override fun accent(): Int = this@MainActivity.accent()
    }

    /** Replit: día en que se renuevan los créditos del mes (empieza el ciclo de facturación, 00:00). */
    private fun pickRenewalDate(acc: Account) {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Día de renovación mensual")
            .setCalendarConstraints(
                CalendarConstraints.Builder().setValidator(DateValidatorPointForward.now()).build()
            )
            .build()
        picker.addOnPositiveButtonClickListener { utcMillis ->
            // El selector devuelve la fecha en UTC: se toma ese día y se pasa a las 00:00 locales.
            val utc = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
            val target = Calendar.getInstance().apply {
                set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (target.timeInMillis <= System.currentTimeMillis()) target.add(Calendar.DAY_OF_YEAR, 1)
            setLimit(acc, target.timeInMillis)
        }
        picker.show(supportFragmentManager, "limit_date")
    }

    private fun setLimit(acc: Account, until: Long) {
        // El aviso de fin de descanso es una notificación: pide el permiso la primera vez.
        if (until > 0L) askNotificationPermissionThen { }
        acc.limitUntil = until
        acc.limitStart = if (until > 0L) System.currentTimeMillis() else 0L
        if (until <= 0L) acc.autoReply = ""
        onAccountsChanged()
        refreshChipTexts()
        updateInfo()
        if (until > 0L && acc.id == currentId) {
            val alt = accounts.firstOrNull { it.id != acc.id && !it.limitActive() }
            if (alt != null) snack("«${acc.name}» en descanso", "Ir a «${alt.name}»") { select(alt.id) }
        }
        if (until > 0L) askAutoReply(acc)
    }

    // ---------------------------------------------------------------- Respuesta automática

    /** Después de marcar el límite: ofrece un mensaje guardado (o propio) para enviar solo al terminar el descanso. */
    private fun askAutoReply(acc: Account) {
        val snips = store.snippets
        val labels = ArrayList<String>()
        labels.add("No responder")
        snips.forEach { labels.add(it.take(70)) }
        labels.add("Texto propio…")
        val cur = acc.autoReply
        val checked = when {
            cur.isEmpty() -> 0
            snips.indexOf(cur) >= 0 -> snips.indexOf(cur) + 1
            else -> labels.size - 1
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Respuesta automática en «${acc.name}»")
            .setSingleChoiceItems(labels.toTypedArray(), checked) { d, which ->
                d.dismiss()
                when {
                    which == 0 -> setAutoReply(acc, "")
                    which == labels.size - 1 -> ownAutoReply(acc)
                    else -> setAutoReply(acc, snips[which - 1])
                }
            }
            .setNegativeButton("Ahora no", null)
            .show()
    }

    private fun ownAutoReply(acc: Account) {
        val input = TextInputEditText(this)
        input.setText(acc.autoReply)
        input.minLines = 2
        input.maxLines = 6
        val holder = FrameLayout(this)
        holder.setPadding(dp(20), dp(8), dp(20), 0)
        holder.addView(input, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        MaterialAlertDialogBuilder(this)
            .setTitle("Mensaje automático")
            .setView(holder)
            .setPositiveButton("Guardar") { _, _ -> setAutoReply(acc, input.text?.toString().orEmpty().trim()) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun setAutoReply(acc: Account, text: String) {
        acc.autoReply = text.take(4000)
        store.save(accounts)
        snack(if (text.isEmpty()) "Sin respuesta automática" else "Se enviará sola «${text.take(40)}» al terminar el descanso, sin abrir la app")
    }

    private val autoQueue = ArrayDeque<String>()
    private var autoBusy = false

    private fun enqueueAutoReply(a: Account) {
        if (autoQueue.contains(a.id)) return
        autoQueue.add(a.id)
        processAutoQueue()
    }

    /** Una respuesta a la vez: abre la cuenta, recarga la página y, cuando aparece la caja de chat, escribe y envía. */
    private fun processAutoQueue() {
        if (autoBusy) return
        val id = autoQueue.removeFirstOrNull() ?: return
        val acc = accounts.firstOrNull { it.id == id }
        if (acc == null || acc.autoReply.isEmpty() || acc.limitActive()) {
            processAutoQueue()
            return
        }
        autoBusy = true
        val text = acc.autoReply
        acc.autoReply = "" // se limpia antes de enviar: nunca se manda dos veces
        store.save(accounts)
        select(acc.id)
        reloadWithLogo(acc.id)
        snack("Enviando la respuesta automática a «${acc.name}»…")
        handler.postDelayed({ tryAutoSend(acc, text, 0) }, 6000)
    }

    private fun finishAutoReply() {
        autoBusy = false
        processAutoQueue()
    }

    private fun tryAutoSend(acc: Account, text: String, attempt: Int) {
        val wv = webViews[acc.id]
        if (wv == null) {
            finishAutoReply()
            return
        }
        val js = AutoSend.js(text)
        wv.evaluateJavascript(js) { res ->
            if (res?.contains("ok") == true) {
                snack("Respuesta enviada a «${acc.name}»")
                LimitAlarms.notifyResult(this, acc.id, acc.name, true, text)
                finishAutoReply()
            } else if (attempt < 25) {
                handler.postDelayed({ tryAutoSend(acc, text, attempt + 1) }, 2000)
            } else {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("AlternAIs", text))
                snack("No pude enviar la respuesta a «${acc.name}»: copié el mensaje, pegalo en el chat")
                LimitAlarms.notifyResult(this, acc.id, acc.name, false, text)
                finishAutoReply()
            }
        }
    }

    // ---------------------------------------------------------------- Borrado

    private fun wipeAccount(acc: Account, remove: Boolean) {
        webViews.remove(acc.id)?.let { wv ->
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.destroy()
        }
        failed.remove(acc.id)
        loadingIds.remove(acc.id)
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
