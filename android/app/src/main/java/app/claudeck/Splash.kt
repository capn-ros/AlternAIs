package app.claudeck

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.graphics.PathParser
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
 * (el tiempo de descanso de cada cuenta). Debajo va el nombre "AlternAIs" (trazo redondeado): en el arranque
 * se dibuja letra a letra; en el bucle cada letra se voltea y cambia de color en cascada, al compás del logo
 * (ciclo de 2 vueltas). Se dibuja a mano; no usa recursos ni librerías.
 */
class SplashLogoView(context: Context, private val loop: Boolean = false, private val light: Boolean = false) : View(context) {
    private val coral = 0xFFD97757.toInt()
    // En tema claro la mitad crema y las letras crema pasan a carbón (sobre blanco no se verían).
    private val cream = if (light) 0xFF1C1917.toInt() else 0xFFF5EFE6.toInt()
    private val dots = intArrayOf(0xFFE07A5F.toInt(), 0xFFF2A33A.toInt(), 0xFF81B29A.toInt(), 0xFF3FB0C4.toInt(), 0xFF9B72CF.toInt())
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var t = 0f
    private var wt = 0f // tiempo del nombre (en bucle: 0..2*LOOP)

    // Nombre: [trazo, ancho, color]. Mismo trazo que en Windows (splash.js).
    private class Glyph(val path: Path, val w: Float, val base: Int, val alt: Int, val x: Float) {
        val measure = PathMeasure(path, false)
        val len = measure.length
    }
    private val glyphs: List<Glyph>
    private val wTotal: Float
    private val seg = Path()

    init {
        val defs = listOf(
            Triple("M0 0L13 -36L26 0M5 -13H21", 26f, cream),
            Triple("M3 -40V0", 6f, cream),
            Triple("M6 -36V-9A9 9 0 0 0 15 0M0 -26H16", 17f, cream),
            Triple("M0 -13H26A13 13 0 1 0 22.2 -3.8", 26f, cream),
            Triple("M3 0V-26M3 -14C3 -22 8 -26 16 -26", 17f, cream),
            Triple("M3 0V-26M3 -15C3 -23 8 -26 14 -26C21 -26 25 -22 25 -14V0", 28f, cream),
            Triple("M0 0L13 -36L26 0M5 -13H21", 26f, coral),
            Triple("M3 -36V0", 6f, coral),
            Triple("M17 -21C15 -26 4 -28 2.5 -20C1.5 -13 18 -14 18 -6C18 1 5 1 1 -6", 20f, cream)
        )
        var x = 0f
        val list = ArrayList<Glyph>()
        for ((d, w, c) in defs) {
            list.add(Glyph(PathParser.createPathFromPathData(d), w, c, if (c == coral) cream else coral, x))
            x += w + 8f
        }
        glyphs = list
        wTotal = x - 8f
    }
    private var animator: ValueAnimator? = null

    /** Se llama al terminar (o al tocar la pantalla). */
    var onDone: (() -> Unit)? = null

    init {
        setBackgroundColor(if (light) 0xFFFFFFFF.toInt() else 0xFF1C1917.toInt())
        isClickable = true
        if (!loop) setOnClickListener { finish() }
    }

    /** En modo bucle (mientras carga una página): repite giro, volteo y anillo, sin la entrada. */
    fun startLoop() {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, LOOP * 2f).apply {
            duration = (LOOP * 2f).toLong()
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                val v = it.animatedValue as Float
                wt = v
                t = LOOP_START + v % LOOP
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
                wt = t
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
        val cy = height / 2f - 25f * u // el nombre va debajo; el conjunto queda centrado

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

        drawWord(canvas, cx, cy + 122f * u, u * 0.7f)
    }

    private fun drawWord(canvas: Canvas, x0: Float, baseY: Float, k: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 6f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        canvas.save()
        canvas.translate(x0 - wTotal * k / 2f, baseY)
        canvas.scale(k, k)
        for ((i, g) in glyphs.withIndex()) {
            var sx = 1f
            var hop = 0f
            var swapped = false
            var draw = 1f
            if (!loop) {
                draw = easeOut(clamp((wt - IN_START - IN_STAG * i) / IN_DUR))
            } else {
                val p1 = easeInOut(clamp((wt - WAVE1 - W_STAG * i) / W_DUR))
                val p2 = easeInOut(clamp((wt - WAVE2 - W_STAG * i) / W_DUR))
                swapped = p1 >= 0.5f && p2 < 0.5f
                sx = abs(cos(PI.toFloat() * (if (p2 > 0f && p2 < 1f) p2 else p1))).coerceAtLeast(0.02f)
                hop = -4f * (sin(PI.toFloat() * p1) + sin(PI.toFloat() * p2))
            }
            if (draw <= 0.001f) continue
            paint.color = if (swapped) g.alt else g.base
            paint.alpha = 255
            canvas.save()
            canvas.translate(g.x + g.w / 2f, -18f + hop)
            canvas.scale(sx, 1f)
            canvas.translate(-g.w / 2f, 18f)
            if (draw >= 0.999f) {
                canvas.drawPath(g.path, paint)
            } else {
                // Dibujo progresivo: cada subtrazo se recorta al mismo porcentaje.
                seg.rewind()
                g.measure.setPath(g.path, false)
                do {
                    g.measure.getSegment(0f, g.measure.length * draw, seg, true)
                } while (g.measure.nextContour())
                canvas.drawPath(seg, paint)
            }
            canvas.restore()
        }
        canvas.restore()
    }

    companion object {
        const val TOTAL = 1700f
        const val LOOP_START = 300f
        const val LOOP = 1300f
        const val WAVE1 = 250f
        const val WAVE2 = 1550f
        const val W_STAG = 55f
        const val W_DUR = 460f
        const val IN_START = 450f
        const val IN_STAG = 60f
        const val IN_DUR = 380f
    }
}
