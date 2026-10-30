package app.claudeck

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.content.Context
import android.view.View
import androidx.core.graphics.ColorUtils

/** Utilidades de dibujo compartidas por la Activity (atajos) y la burbuja flotante. */
object Ui {

    /** Primera letra/número del nombre, en mayúscula (para el avatar de la cuenta). */
    fun initial(name: String): String = app.claudeck.domain.policy.AccountInitial.of(name)

    /**
     * Avatar circular de color con una letra. [ring] dibuja un aro blanco (cuenta actual),
     * [resting] agrega un punto ámbar arriba a la derecha (cuenta en descanso por límite).
     */
    fun avatar(
        color: Int,
        text: String,
        size: Int,
        ring: Boolean = false,
        resting: Boolean = false,
        ringColor: Int = Color.WHITE
    ): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = size / 2f

        p.style = Paint.Style.FILL
        p.color = color
        canvas.drawCircle(r, r, r - size * 0.03f, p)

        if (ring) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = size * 0.08f
            p.color = ringColor
            canvas.drawCircle(r, r, r - p.strokeWidth / 2f, p)
        }

        p.style = Paint.Style.FILL
        p.color = if (ColorUtils.calculateLuminance(color) > 0.55) Color.parseColor("#1F1F1F") else Color.WHITE
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = size * 0.44f
        p.textAlign = Paint.Align.CENTER
        val baseline = r - (p.descent() + p.ascent()) / 2f
        canvas.drawText(text, r, baseline, p)

        if (resting) {
            val cx = size * 0.80f
            val cy = size * 0.20f
            p.color = Color.WHITE
            canvas.drawCircle(cx, cy, size * 0.17f, p)
            p.color = Color.parseColor("#F2A541")
            canvas.drawCircle(cx, cy, size * 0.12f, p)
        }
        return bmp
    }

    /**
     * Círculo de cuenta con reloj de espera: el anillo exterior es el tiempo que falta de descanso
     * ([fraction] 1 = recién marcado; se va "borrando" en sentido horario hasta 0). Sin límite
     * activo no lleva anillo. [selected] agrega una rayita debajo. El disco queda siempre al
     * centro vertical del bitmap, que es [extra] px más alto arriba y abajo.
     */
    fun timerAvatar(
        color: Int,
        text: String,
        size: Int,
        extra: Int,
        fraction: Float,
        ringColor: Int,
        selected: Boolean,
        markColor: Int
    ): Bitmap {
        val h = size + extra * 2
        val bmp = Bitmap.createBitmap(size, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val cx = size / 2f
        val cy = h / 2f
        val stroke = size * 0.07f
        val ringR = size / 2f - stroke / 2f
        val diskR = ringR - stroke / 2f - size * 0.05f

        if (fraction > 0f) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = stroke
            p.strokeCap = Paint.Cap.ROUND
            p.color = ColorUtils.setAlphaComponent(ringColor, 50)
            canvas.drawCircle(cx, cy, ringR, p)
            p.color = ringColor
            val oval = RectF(cx - ringR, cy - ringR, cx + ringR, cy + ringR)
            canvas.drawArc(oval, -90f, 360f * fraction.coerceAtLeast(0.02f), false, p)
        }

        p.style = Paint.Style.FILL
        p.color = color
        canvas.drawCircle(cx, cy, diskR, p)

        p.color = if (ColorUtils.calculateLuminance(color) > 0.55) Color.parseColor("#1F1F1F") else Color.WHITE
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = diskR * 0.95f
        p.textAlign = Paint.Align.CENTER
        canvas.drawText(text, cx, cy - (p.descent() + p.ascent()) / 2f, p)

        if (selected) {
            p.color = markColor
            val mw = size * 0.17f
            val mh = extra * 0.42f
            val my = h - extra / 2f
            canvas.drawRoundRect(RectF(cx - mw, my - mh / 2f, cx + mw, my + mh / 2f), mh / 2f, mh / 2f, p)
        }
        return bmp
    }
}

/** Barra de tiempo para la barra plegada: relleno = lo que falta de descanso, con texto adentro. */
class TimeBar(context: Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var fraction = 1f
    private var label = ""
    private var color = Color.GRAY
    private var textOnTrack = Color.WHITE

    fun set(fraction: Float, label: String, color: Int, textOnTrack: Int) {
        this.fraction = fraction.coerceIn(0f, 1f)
        this.label = label
        this.color = color
        this.textOnTrack = textOnTrack
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val r = h / 2f
        rect.set(0f, 0f, w, h)

        p.style = Paint.Style.FILL
        p.color = ColorUtils.setAlphaComponent(color, 60)
        canvas.drawRoundRect(rect, r, r, p)

        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = h * 0.5f
        p.textAlign = Paint.Align.CENTER
        val n = p.breakText(label, true, w - h, null)
        val shown = if (n < label.length) label.substring(0, n).trimEnd() + "…" else label
        val base = h / 2f - (p.descent() + p.ascent()) / 2f

        p.color = textOnTrack
        canvas.drawText(shown, w / 2f, base, p)

        val fw = w * fraction
        if (fw > 0f) {
            canvas.save()
            canvas.clipRect(0f, 0f, fw, h)
            p.color = color
            canvas.drawRoundRect(rect, r, r, p)
            p.color = if (ColorUtils.calculateLuminance(color) > 0.55) Color.parseColor("#1F1F1F") else Color.WHITE
            canvas.drawText(shown, w / 2f, base, p)
            canvas.restore()
        }
    }
}
