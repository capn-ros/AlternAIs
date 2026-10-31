package app.claudeck.ui.dock

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.AbstractComposeView
import app.claudeck.domain.model.AvatarColors
import app.claudeck.domain.model.TimeBarLayout

/**
 * Qué dibuja la barra de tiempo: [fraction] es lo que falta de descanso (1 = lleno), [color] el de la
 * cuenta y [textOnTrack] el color del texto sobre la parte vacía.
 */
data class TimeBarState(
    val fraction: Float = 1f,
    val label: String = "",
    val color: Int = 0xFF888888.toInt(),
    val textOnTrack: Int = 0xFFFFFFFF.toInt()
)

/** Pastilla con el texto adentro; la parte llena la pinta del color de la cuenta y el texto cambia de color. */
@Composable
fun TimeBarCanvas(state: TimeBarState, modifier: Modifier = Modifier.fillMaxSize()) {
    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
    }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val radius = CornerRadius(TimeBarLayout.cornerRadius(h))
        val track = Size(w, h)

        drawRoundRect(Color(state.color).copy(alpha = 60f / 255f), size = track, cornerRadius = radius)

        paint.textSize = TimeBarLayout.textSize(h)
        val fits = paint.breakText(state.label, true, TimeBarLayout.textAvailableWidth(w, h), null)
        val shown = TimeBarLayout.ellipsize(state.label, fits)
        val base = TimeBarLayout.baseline(h, paint.ascent(), paint.descent())

        drawIntoCanvas { canvas ->
            paint.color = state.textOnTrack
            canvas.nativeCanvas.drawText(shown, w / 2f, base, paint)
        }

        val fillWidth = TimeBarLayout.fillWidth(w, state.fraction)
        if (fillWidth > 0f) {
            clipRect(left = 0f, top = 0f, right = fillWidth, bottom = h) {
                drawRoundRect(Color(state.color), size = track, cornerRadius = radius)
                drawIntoCanvas { canvas ->
                    paint.color = AvatarColors.textOn(state.color)
                    canvas.nativeCanvas.drawText(shown, w / 2f, base, paint)
                }
            }
        }
    }
}

/** La barra de tiempo como View: se actualiza con [set], igual que la anterior. */
class TimeBarView(context: Context) : AbstractComposeView(context) {
    private var state by mutableStateOf(TimeBarState())

    fun set(fraction: Float, label: String, color: Int, textOnTrack: Int) {
        state = TimeBarState(TimeBarLayout.clampFraction(fraction), label, color, textOnTrack)
    }

    @Composable
    override fun Content() {
        TimeBarCanvas(state)
    }
}
