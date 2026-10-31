package app.claudeck

import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import app.claudeck.ui.snippets.SnippetsActions
import app.claudeck.ui.snippets.SnippetsViewModel
import app.claudeck.ui.snippets.createSnippetsView

/** Lo que el cajón de mensajes le pide a la pantalla principal. */
interface SnippetsHost {
    fun surface(): Int
    fun onSurface(): Int
    fun accent(): Int
    fun border(): Int
    fun close()
    fun message(text: String)
    fun insert(text: String)
}

/**
 * Cajón de mensajes guardados. El marco (borde de color de la cuenta, esquinas, animaciones y gesto de
 * cerrar) sigue siendo una vista de Android; el contenido es Compose (`ui/snippets`) con su ViewModel.
 */
class SnippetsPanel(private val act: AppCompatActivity, private val host: SnippetsHost, vm: SnippetsViewModel) {
    val view: LinearLayout = LinearLayout(act)

    init {
        val r = dp(22).toFloat()
        view.orientation = LinearLayout.VERTICAL
        view.elevation = dp(8).toFloat()
        view.isClickable = true
        view.visibility = View.GONE
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            setColor(host.surface())
            setStroke(dp(3), host.border())
        }
        view.clipToOutline = true
        view.setPadding(0, dp(10), 0, 0)

        val actions = object : SnippetsActions {
            override fun close() = host.close()
            override fun insert(text: String) = host.insert(text)
            override fun message(text: String) = host.message(text)
        }
        view.addView(
            createSnippetsView(act, vm, actions, host.surface(), host.onSurface(), host.accent()),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )
    }

    /** Cambia el color del borde del cajón (sigue a la cuenta activa). */
    fun setBorder(color: Int) {
        (view.background as? GradientDrawable)?.setStroke(dp(3), color)
    }

    private fun dp(v: Int): Int = (v * act.resources.displayMetrics.density).toInt()
}
