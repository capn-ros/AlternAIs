package app.claudeck.ui.dock

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.graphics.ColorUtils

/**
 * Píldora con el nombre de una IA. En la barra de cuentas muestra la IA actual (con ▾) y abre el selector; en la
 * pantalla vacía hay una por IA, con la actual resaltada. El color de resalte es el de la cuenta activa.
 */
class AiPill(context: Context) : AppCompatTextView(context) {
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    init {
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(14), dp(7), dp(14), dp(7))
        minHeight = dp(36)
        isClickable = true
        isFocusable = true
        maxLines = 1
    }

    fun bind(label: String, accent: Int, fg: Int, selected: Boolean) {
        text = label
        setTextColor(fg)
        background = GradientDrawable().apply {
            cornerRadius = dp(18).toFloat()
            setColor(ColorUtils.setAlphaComponent(if (selected) accent else fg, if (selected) 0x2E else 0x10))
            setStroke(dp(if (selected) 2 else 1), if (selected) accent else ColorUtils.setAlphaComponent(fg, 0x33))
        }
    }
}
