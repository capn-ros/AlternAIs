package app.claudeck

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * Envía un mensaje en el chat de una cuenta SIN abrir la app: carga la última página de esa cuenta en un
 * WebView invisible (con su misma sesión), escribe el texto, lo envía y avisa el resultado. Se usa para la
 * respuesta automática al terminar el descanso y para responder desde la notificación.
 */
class AutoReplyService : android.app.Service() {
    companion object {
        private const val CHANNEL = "autoreply"
        private const val NOTIF_ID = 9

        /** Devuelve false si Android no dejó arrancar el servicio. */
        fun start(ctx: Context, id: String, text: String?): Boolean = try {
            val i = Intent(ctx, AutoReplyService::class.java).putExtra("id", id)
            if (text != null) i.putExtra("text", text)
            ContextCompat.startForegroundService(ctx, i)
            true
        } catch (_: Exception) {
            false
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val jobs = HashSet<String>()
    private var wake: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    private fun foreground(name: String) {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Envío de respuestas", NotificationManager.IMPORTANCE_LOW))
        }
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_bubble)
            .setContentTitle("Enviando mensaje a «$name»…")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIF_ID, n, type)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra("id")
        if (id == null) {
            foreground("la cuenta")
            stopSelf()
            return START_NOT_STICKY
        }
        val store = AccountStore(this)
        var svc: Service? = null
        var list: List<Account> = emptyList()
        for (s in Service.values()) {
            val l = store.loadFor(s)
            if (l.any { it.id == id }) { svc = s; list = l; break }
        }
        val acc = list.firstOrNull { it.id == id }
        foreground(acc?.name ?: "la cuenta")
        if (acc == null || svc == null || !jobs.add(id)) {
            finish(id)
            return START_NOT_STICKY
        }
        var text = intent.getStringExtra("text")
        if (text == null) {
            // Mensaje automático: se limpia antes de enviar, así nunca se manda dos veces.
            text = acc.autoReply
            if (text.isEmpty()) { finish(id); return START_NOT_STICKY }
            acc.autoReply = ""
            store.saveFor(svc, list)
        }
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AlternAIs:autoreply").apply { acquire(120_000L) }
        } catch (_: Exception) {
        }
        sendIn(svc, acc, text)
        return START_NOT_STICKY
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun sendIn(svc: Service, acc: Account, text: String) {
        val wv = WebView(applicationContext)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            ProfileStore.getInstance().getOrCreateProfile(acc.profileName)
            WebViewCompat.setProfile(wv, acc.profileName)
        }
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
        }
        // Sin ventana: se le da tamaño y se marca como visible para que la página se dibuje y responda.
        val dm = resources.displayMetrics
        wv.measure(
            View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.EXACTLY)
        )
        wv.layout(0, 0, dm.widthPixels, dm.heightPixels)
        wv.onResume()
        wv.dispatchWindowVisibilityChanged(View.VISIBLE)
        var done = false
        fun end(ok: Boolean) {
            if (done) return
            done = true
            handler.removeCallbacksAndMessages(null)
            LimitAlarms.notifyResult(this, acc.id, acc.name, ok, text)
            try { wv.stopLoading(); wv.destroy() } catch (_: Exception) {}
            finish(acc.id)
        }
        fun attempt(n: Int) {
            if (done) return
            wv.evaluateJavascript(AutoSend.js(text)) { res ->
                if (done) return@evaluateJavascript
                if (res?.contains("ok") == true) {
                    // El script hace el clic de enviar ~0,6 s después: se espera a que salga la petición.
                    handler.postDelayed({ end(true) }, 3500)
                } else if (n < 22) {
                    handler.postDelayed({ attempt(n + 1) }, 2000)
                } else end(false)
            }
        }
        wv.webViewClient = object : WebViewClient() {
            var started = false
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {}
            override fun onPageFinished(view: WebView?, url: String?) {
                if (started) return
                started = true
                handler.postDelayed({ attempt(0) }, 3000)
            }
        }
        // Tope total: si la página nunca carga, se avisa el fallo.
        handler.postDelayed({ end(false) }, 80_000)
        wv.loadUrl(urlForRestore(svc, acc.lastUrl) ?: svc.startUrl)
    }

    private fun finish(id: String) {
        jobs.remove(id)
        if (jobs.isEmpty()) {
            try { wake?.takeIf { it.isHeld }?.release() } catch (_: Exception) {}
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }
}
