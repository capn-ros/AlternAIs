package app.claudeck.ui.dock

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.unit.sp
import app.claudeck.domain.model.AccountPalette

/** Círculo multicolor (degradé en barrido con los colores de las cuentas) con un «+» blanco adentro. */
@Composable
fun AddAccountButton(modifier: Modifier = Modifier.fillMaxSize()) {
    val colors = remember { AccountPalette.sweepColors().map { Color(it) } }
    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            color = android.graphics.Color.WHITE
            setShadowLayer(3f, 0f, 1f, android.graphics.Color.argb(120, 0, 0, 0))
        }
    }
    Canvas(modifier) {
        drawCircle(brush = Brush.sweepGradient(colors, center = center), radius = size.minDimension / 2f, center = center)
        drawIntoCanvas { canvas ->
            paint.textSize = 24.sp.toPx()
            canvas.nativeCanvas.drawText("+", size.width / 2f, size.height / 2f - (paint.descent() + paint.ascent()) / 2f, paint)
        }
    }
}

/** El botón «+» como View: se usa con toque, descripción y animaciones como cualquier otra. */
class AddAccountButtonView(context: Context) : AbstractComposeView(context) {
    @Composable
    override fun Content() {
        AddAccountButton()
    }
}
