package app.claudeck

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlin.math.abs

/**
 * Burbuja flotante (overlay "sobre otras apps") para cambiar de cuenta desde cualquier lado.
 * Tocar la burbuja despliega la lista de cuentas; tocar una trae Alterna al frente en esa cuenta.
 * Se arrastra y se pega al borde más cercano. Es un servicio en primer plano para que Android
 * no lo mate. Requiere el permiso "Mostrar sobre otras apps" (SYSTEM_ALERT_WINDOW).
 */
class BubbleService : Service() {

    companion object {
        private const val ACTION_STOP = "app.claudeck.action.STOP_BUBBLE"
        private const val CHANNEL_ID = "bubble"
        private const val NOTIF_ID = 7

        /** Instancia viva (solo se toca desde el hilo principal). */
        private var instance: BubbleService? = null

        fun canDraw(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

        fun start(ctx: Context) {
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, BubbleService::class.java))
            } catch (_: Exception) {
            }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, BubbleService::class.java))
        }

        /** Avisa a la burbuja que cambiaron las cuentas o la cuenta activa. */
        fun refresh() {
            instance?.refreshHead()
        }
    }

    private lateinit var wm: WindowManager
    private lateinit var store: AccountStore
    private var root: LinearLayout? = null
    private lateinit var listTop: LinearLayout
    private lateinit var listBottom: LinearLayout
    private lateinit var head: ImageView
    private lateinit var lp: WindowManager.LayoutParams

    private var expanded = false
    private var hx = 0 // posición de la cabeza (esquina superior izquierda, px absolutos)
    private var hy = 0
    private val headSize by lazy { dp(56) }
    private val pad by lazy { dp(6) }
    private val touchSlop by lazy { ViewConfiguration.get(this).scaledTouchSlop }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        store = AccountStore(this)
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            store.bubbleEnabled = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        startAsForeground()
        if (!canDraw(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (root == null) createBubble()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        instance = null
        root?.let {
            try {
                wm.removeView(it)
            } catch (_: Exception) {
            }
        }
        root = null
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (root == null) return
        val (sw, sh) = screenSize()
        hx = hx.coerceIn(0, maxOf(0, sw - headSize))
        hy = hy.coerceIn(0, maxOf(0, sh - headSize))
        collapse(false)
    }

    // ---------------------------------------------------------------- Primer plano

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Burbuja flotante", NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false)
            }
        )
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, BubbleService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bubble)
            .setContentTitle("Burbuja de cuentas activa")
            .setContentText("Tocá la burbuja para cambiar de cuenta")
            .setContentIntent(open)
            .addAction(0, "Ocultar burbuja", stop)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIF_ID, n, type)
    }

    // ---------------------------------------------------------------- Ventana

    private fun screenSize(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.currentWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val m = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(m)
            m.widthPixels to m.heightPixels
        }
    }

    private fun createBubble() {
        val (sw, sh) = screenSize()
        hx = if (store.bubbleX >= 0) store.bubbleX else sw - headSize - dp(4)
        hy = if (store.bubbleY >= 0) store.bubbleY else sh / 3
        hx = hx.coerceIn(0, maxOf(0, sw - headSize))
        hy = hy.coerceIn(0, maxOf(0, sh - headSize))

        head = ImageView(this)
        head.scaleType = ImageView.ScaleType.FIT_XY
        head.elevation = dp(6).toFloat()
        head.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
        head.contentDescription = "Burbuja de cuentas"
        head.setOnTouchListener(headTouch)

        listTop = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        listBottom = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }

        val r = LinearLayout(this)
        r.orientation = LinearLayout.VERTICAL
        r.setPadding(pad, pad, pad, pad)
        r.addView(listTop, LinearLayout.LayoutParams(-2, -2))
        r.addView(head, LinearLayout.LayoutParams(headSize, headSize))
        r.addView(listBottom, LinearLayout.LayoutParams(-2, -2))
        r.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_OUTSIDE && expanded) collapse(true)
            false
        }

        lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = hx - pad
        lp.y = hy - pad

        try {
            wm.addView(r, lp)
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo mostrar la burbuja", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }
        root = r
        refreshHead()
        // Entrada con rebote
        head.scaleX = 0f
        head.scaleY = 0f
        head.animate().scaleX(1f).scaleY(1f).setDuration(380)
            .setInterpolator(OvershootInterpolator(2f)).start()
    }

    private fun applyCollapsedPosition() {
        val r = root ?: return
        lp.x = hx - pad
        lp.y = hy - pad
        try {
            wm.updateViewLayout(r, lp)
        } catch (_: Exception) {
        }
    }

    /** Redibuja la cabeza con el color/letra de la cuenta activa. */
    fun refreshHead() {
        if (root == null) return
        val list = store.load()
        val acc = list.firstOrNull { it.id == store.selectedId } ?: list.firstOrNull()
        val bmp = if (acc == null) {
            Ui.avatar(Color.parseColor("#6B6B6B"), "+", headSize, ring = true)
        } else {
            Ui.avatar(acc.color, Ui.initial(acc.name), headSize, ring = true, resting = acc.limitActive())
        }
        head.setImageBitmap(bmp)
    }

    // ---------------------------------------------------------------- Arrastre y toque

    private val headTouch = object : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var startHx = 0
        private var startHy = 0
        private var dragging = false

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startHx = hx
                    startHy = hy
                    dragging = false
                    v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80).start()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        dragging = true
                        if (expanded) collapse(false)
                    }
                    if (dragging) {
                        val (sw, sh) = screenSize()
                        hx = (startHx + dx.toInt()).coerceIn(0, maxOf(0, sw - headSize))
                        hy = (startHy + dy.toInt()).coerceIn(0, maxOf(0, sh - headSize))
                        applyCollapsedPosition()
                    }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(140)
                        .setInterpolator(OvershootInterpolator()).start()
                    if (dragging) {
                        snapToEdge()
                    } else if (e.actionMasked == MotionEvent.ACTION_UP) {
                        if (expanded) collapse(true) else expand()
                    }
                    return true
                }
            }
            return false
        }
    }

    private fun snapToEdge() {
        val (sw, _) = screenSize()
        val margin = dp(4)
        val target = if (hx + headSize / 2 < sw / 2) margin else sw - headSize - margin
        val anim = ValueAnimator.ofInt(hx, target)
        anim.duration = 220
        anim.interpolator = DecelerateInterpolator()
        anim.addUpdateListener {
            hx = it.animatedValue as Int
            applyCollapsedPosition()
        }
        anim.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                store.bubbleX = hx
                store.bubbleY = hy
            }
        })
        anim.start()
    }

    // ---------------------------------------------------------------- Lista de cuentas

    private fun expand() {
        val r = root ?: return
        val accounts = store.load()
        if (accounts.isEmpty()) {
            openApp(null)
            return
        }
        expanded = true
        val (sw, sh) = screenSize()
        val right = hx + headSize / 2 > sw / 2
        val above = hy + headSize / 2 > sh / 2
        val g = if (right) Gravity.END else Gravity.START

        listTop.removeAllViews()
        listBottom.removeAllViews()
        val target = if (above) listTop else listBottom

        val rows = mutableListOf<LinearLayout>()
        accounts.forEach { acc ->
            val current = acc.id == (store.selectedId ?: accounts.first().id)
            val bmp = Ui.avatar(acc.color, Ui.initial(acc.name), dp(44), ring = current, resting = acc.limitActive())
            val label = if (acc.limitActive()) "${acc.name}  ⏳" else acc.name
            rows.add(buildRow(label, bmp, right) { switchTo(acc.id) })
        }
        val hide = buildRow("Ocultar burbuja", Ui.avatar(Color.parseColor("#4A4A4A"), "✕", dp(44)), right) { hideBubble() }
        // La fila "ocultar" va lo más lejos posible de la cabeza para no tocarla sin querer.
        val ordered = if (above) listOf(hide) + rows else rows + hide
        ordered.forEach { target.addView(it) }

        listTop.visibility = if (above) View.VISIBLE else View.GONE
        listBottom.visibility = if (above) View.GONE else View.VISIBLE
        setGravity(listTop, g)
        setGravity(listBottom, g)
        setGravity(head, g)

        r.measure(
            View.MeasureSpec.makeMeasureSpec(sw, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(sh, View.MeasureSpec.AT_MOST)
        )
        val w = r.measuredWidth
        val h = r.measuredHeight
        lp.x = (if (right) hx + headSize + pad - w else hx - pad).coerceIn(0, maxOf(0, sw - w))
        lp.y = (if (above) hy + headSize + pad - h else hy - pad).coerceIn(0, maxOf(0, sh - h))
        try {
            wm.updateViewLayout(r, lp)
        } catch (_: Exception) {
        }

        // Animación en cascada, desde la fila más cercana a la cabeza hacia afuera.
        val nearestFirst = if (above) ordered.reversed() else ordered
        nearestFirst.forEachIndexed { i, row ->
            row.alpha = 0f
            row.scaleX = 0.6f
            row.scaleY = 0.6f
            row.translationY = if (above) dp(24).toFloat() else -dp(24).toFloat()
            row.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                .setStartDelay(i * 35L).setDuration(260)
                .setInterpolator(OvershootInterpolator(1.4f)).start()
        }
    }

    private fun setGravity(v: View, g: Int) {
        val l = v.layoutParams as LinearLayout.LayoutParams
        l.gravity = g
        v.layoutParams = l
    }

    private fun buildRow(label: String, avatar: android.graphics.Bitmap, right: Boolean, onClick: () -> Unit): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val rlp = LinearLayout.LayoutParams(-2, -2)
        rlp.topMargin = dp(4)
        rlp.bottomMargin = dp(4)
        rlp.gravity = if (right) Gravity.END else Gravity.START
        row.layoutParams = rlp

        val av = ImageView(this)
        av.setImageBitmap(avatar)
        av.elevation = dp(4).toFloat()
        av.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
        val avLp = LinearLayout.LayoutParams(dp(44), dp(44))

        val tv = TextView(this)
        tv.text = label
        tv.setTextColor(Color.WHITE)
        tv.textSize = 14f
        tv.maxLines = 1
        tv.setPadding(dp(12), dp(7), dp(12), dp(7))
        val bg = GradientDrawable()
        bg.cornerRadius = dp(18).toFloat()
        bg.setColor(Color.parseColor("#E6232323"))
        tv.background = bg
        tv.elevation = dp(3).toFloat()
        val tvLp = LinearLayout.LayoutParams(-2, -2)
        tvLp.setMargins(dp(8), 0, dp(8), 0)

        if (right) {
            row.addView(tv, tvLp)
            row.addView(av, avLp)
        } else {
            row.addView(av, avLp)
            row.addView(tv, tvLp)
        }
        row.setOnClickListener { onClick() }
        return row
    }

    private fun collapse(animated: Boolean) {
        if (!expanded && listTop.visibility == View.GONE && listBottom.visibility == View.GONE) {
            applyCollapsedPosition()
            return
        }
        expanded = false
        if (animated) {
            listOf(listTop, listBottom).forEach { l ->
                for (i in 0 until l.childCount) l.getChildAt(i).animate().alpha(0f).setDuration(110).start()
            }
            head.postDelayed({ if (!expanded) hardCollapse() }, 120)
        } else {
            hardCollapse()
        }
    }

    private fun hardCollapse() {
        listTop.removeAllViews()
        listBottom.removeAllViews()
        listTop.visibility = View.GONE
        listBottom.visibility = View.GONE
        applyCollapsedPosition()
    }

    private fun switchTo(id: String) {
        store.selectedId = id
        refreshHead()
        collapse(true)
        openApp(id)
    }

    private fun hideBubble() {
        store.bubbleEnabled = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun openApp(id: String?) {
        val i = Intent(this, MainActivity::class.java)
        if (id != null) i.putExtra(MainActivity.EXTRA_SELECT, id)
        i.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        )
        try {
            startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(this, "Abrí Alterna para cambiar de cuenta", Toast.LENGTH_LONG).show()
        }
    }
}
