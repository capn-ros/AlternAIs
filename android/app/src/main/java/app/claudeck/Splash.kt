package app.claudeck

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Animación de arranque del logo (~1,7 s). Cuenta lo que hace la app:
 * las dos mitades (Claude y Replit) entran y se encajan, giran para "alternar", aparecen las cuentas
 * (puntos de colores), se voltean cambiando de color (cambio de IA / de tema) y un anillo se completa
 * (el tiempo de descanso de cada cuenta). Se dibuja a mano; no usa recursos ni librerías.
 */
class SplashLogoView(context: Context, private val loop: Boolean = false) : View(context) {
    private val coral = 0xFFD97757.toInt()
    private val cream = 0xFFF5EFE6.toInt()
    private val dots = intArrayOf(0xFFE07A5F.toInt(), 0xFFF2A33A.toInt(), 0xFF81B29A.toInt(), 0xFF3FB0C4.toInt(), 0xFF9B72CF.toInt())
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var t = 0f
    private var animator: ValueAnimator? = null

    /** Se llama al terminar (o al tocar la pantalla). */
    var onDone: (() -> Unit)? = null

    init {
        setBackgroundColor(0xFF1C1917.toInt())
        isClickable = true
        if (!loop) setOnClickListener { finish() }
    }

    /** En modo bucle (mientras carga una página): repite giro, volteo y anillo, sin la entrada. */
    fun startLoop() {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, LOOP).apply {
            duration = LOOP.toLong()
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                t = LOOP_START + it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun stopLoop() {
        animator?.cancel()
        animator = null
    }

    fun start() {
        animator = ValueAnimator.ofFloat(0f, TOTAL).apply {
            duration = TOTAL.toLong()
            interpolator = LinearInterpolator()
            addUpdateListener {
                t = it.animatedValue as Float
                alpha = 1f - clamp((t - 1500f) / 200f)
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    onDone?.invoke()
                }
            })
            start()
        }
    }

    fun finish() {
        animator?.let {
            it.removeAllListeners()
            it.cancel()
        }
        animator = null
        onDone?.invoke()
        onDone = null
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    private fun clamp(x: Float) = if (x < 0f) 0f else if (x > 1f) 1f else x
    private fun easeInOut(x: Float) = if (x < 0.5f) 2f * x * x else 1f - (-2f * x + 2f).pow(2) / 2f
    private fun easeOut(x: Float) = 1f - (1f - x).pow(3)
    private fun easeOutBack(x: Float): Float {
        val c1 = 1.70158f
        val c3 = c1 + 1f
        return 1f + c3 * (x - 1f).pow(3) + c1 * (x - 1f).pow(2)
    }

    override fun onDraw(canvas: Canvas) {
        val u = min(width, height) * 0.5f / 120f // unidad: el logo mide 120 u
        val cx = width / 2f
        val cy = height / 2f * 0.96f

        val appear = clamp(t / 150f)
        val slide = easeOutBack(clamp(t / 300f))
        val rot = 180f * easeInOut(clamp((t - 300f) / 450f))
        val flip = easeInOut(clamp((t - 750f) / 350f))
        val ring = easeOut(clamp((t - 1100f) / 350f))
        val pulse = 1f + 0.08f * sin(PI.toFloat() * clamp((t - 1350f) / 200f))

        // Mitad A (Claude, a la izquierda al inicio) y mitad B (Replit).
        val swapped = flip >= 0.5f
        val colorA = if (swapped) cream else coral
        val colorB = if (swapped) coral else cream
        val scaleX = abs(cos(PI.toFloat() * flip)).coerceAtLeast(0.02f)

        // Cuentas: puntos que aparecen en órbita con rebote, escalonados.
        for (i in dots.indices) {
            val s = easeOutBack(clamp((t - 400f - 60f * i) / 220f)) * (1f - ring)
            if (s <= 0.01f) continue
            val ang = Math.toRadians((-90.0 + 72.0 * i) + rot * 0.5)
            val x = cx + cos(ang).toFloat() * 54f * u
            val y = cy + sin(ang).toFloat() * 54f * u
            paint.style = Paint.Style.FILL
            paint.color = dots[i]
            paint.alpha = 255
            canvas.drawCircle(x, y, 5.5f * u * s, paint)
        }

        // Anillo que se completa (el tiempo de descanso).
        if (ring > 0f) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f * u
            paint.strokeCap = Paint.Cap.ROUND
            paint.color = coral
            paint.alpha = if (loop) ((1f - clamp((t - 1450f) / 150f)) * 255).toInt() else 255
            rect.set(cx - 46f * u, cy - 46f * u, cx + 46f * u, cy + 46f * u)
            canvas.drawArc(rect, -90f, 360f * ring, false, paint)
        }

        // Logo.
        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(pulse, pulse)
        canvas.rotate(rot)
        canvas.scale(scaleX, 1f)
        rect.set(-32f * u, -32f * u, 32f * u, 32f * u)
        paint.style = Paint.Style.FILL
        // A: izquierda, un poco abajo.
        canvas.save()
        canvas.translate((-1f - 60f * (1f - slide)) * u, 5f * u)
        paint.color = colorA
        paint.alpha = (appear * 255).toInt()
        canvas.drawArc(rect, 90f, 180f, true, paint)
        canvas.restore()
        // B: derecha, un poco arriba.
        canvas.save()
        canvas.translate((1f + 60f * (1f - slide)) * u, -5f * u)
        paint.color = colorB
        paint.alpha = (appear * 255).toInt()
        canvas.drawArc(rect, 270f, 180f, true, paint)
        canvas.restore()
        canvas.restore()
    }

    companion object {
        const val TOTAL = 1700f
        const val LOOP_START = 300f
        const val LOOP = 1300f
    }
}
