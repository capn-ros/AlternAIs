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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.AbstractComposeView
import app.claudeck.domain.model.AvatarColors
import app.claudeck.domain.model.AvatarGeometry

/**
 * Qué dibuja un círculo de cuenta. [fraction] es lo que falta de descanso (1 = recién marcado, 0 = sin
 * límite: no hay anillo). [selected] agrega la rayita de «cuenta activa».
 */
data class AvatarState(
    val color: Int = 0,
    val initial: String = "",
    val fraction: Float = 0f,
    val ringColor: Int = 0,
    val selected: Boolean = false,
    val markColor: Int = 0
)

/** Disco de color con una letra; si la cuenta descansa, un anillo que se va borrando en sentido horario. */
@Composable
fun AccountAvatar(state: AvatarState, modifier: Modifier = Modifier.fillMaxSize()) {
    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
    }
    Canvas(modifier) {
        val g = AvatarGeometry(size.width, size.height)
        val center = Offset(g.cx, g.cy)
        if (state.fraction > 0f) {
            val ring = Color(state.ringColor)
            val style = Stroke(width = g.stroke, cap = StrokeCap.Round)
            drawCircle(ring.copy(alpha = 50f / 255f), g.ringRadius, center, style = style)
            drawArc(
                color = ring, startAngle = -90f, sweepAngle = g.sweepDegrees(state.fraction), useCenter = false,
                topLeft = Offset(g.cx - g.ringRadius, g.cy - g.ringRadius),
                size = Size(g.ringRadius * 2f, g.ringRadius * 2f), style = style
            )
        }
        drawCircle(Color(state.color), g.diskRadius, center)
        drawIntoCanvas { canvas ->
            paint.color = AvatarColors.textOn(state.color)
            paint.textSize = g.textSize
            canvas.nativeCanvas.drawText(state.initial, g.cx, g.cy - (paint.descent() + paint.ascent()) / 2f, paint)
        }
        if (state.selected) {
            drawRoundRect(
                color = Color(state.markColor),
                topLeft = Offset(g.cx - g.markHalfWidth, g.markCenterY - g.markHeight / 2f),
                size = Size(g.markHalfWidth * 2f, g.markHeight),
                cornerRadius = CornerRadius(g.markHeight / 2f)
            )
        }
    }
}

/**
 * Vista de un círculo de cuenta para la barra: se usa como cualquier View (toque, mantener apretado,
 * animaciones) y se redibuja sola al cambiar [avatar].
 */
class AccountAvatarView(context: Context) : AbstractComposeView(context) {
    var avatar by mutableStateOf(AvatarState())

    @Composable
    override fun Content() {
        AccountAvatar(avatar)
    }
}
