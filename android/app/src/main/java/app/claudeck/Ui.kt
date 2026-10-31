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
}
