package app.claudeck

import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.ColorUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText

/** Lo que el cajón de mensajes le pide a la pantalla principal. */
interface SnippetsHost {
    fun surface(): Int
    fun onSurface(): Int
    fun accent(): Int
    fun border(): Int
    fun close()
    fun message(text: String)
    fun insert(text: String)
    fun snippets(): List<String>
    fun saveSnippets(list: List<String>)
}

/**
 * Cajón de mensajes guardados (con la estética del de ajustes y el explorador). Tocar un mensaje lo escribe
 * en el chat de la cuenta activa; mantener apretado abre sus opciones; «Texto propio» escribe sin guardar.
 */
class SnippetsPanel(private val act: AppCompatActivity, private val host: SnippetsHost) {
    val view: LinearLayout = LinearLayout(act)

    private val onSurface = host.onSurface()
    private val muted = ColorUtils.setAlphaComponent(host.onSurface(), 150)
    private val accent = host.accent()
    private lateinit var column: LinearLayout

    init {
        build()
    }

    private fun dp(v: Int): Int = (v * act.resources.displayMetrics.density).toInt()

    private fun build() {
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

        val head = LinearLayout(act)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(dp(16), 0, dp(8), dp(4))
        val texts = LinearLayout(act)
        texts.orientation = LinearLayout.VERTICAL
        val title = TextView(act)
        title.text = "Mensajes guardados"
        title.textSize = 18f
        title.setTypeface(title.typeface, android.graphics.Typeface.BOLD)
        title.setTextColor(onSurface)
        val hint = TextView(act)
        hint.text = "Tocá uno para escribirlo en el chat · mantené apretado para sus opciones"
        hint.textSize = 12f
        hint.setTextColor(muted)
        texts.addView(title)
        texts.addView(hint)
        head.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val close = ImageButton(act)
        close.setImageResource(R.drawable.ic_close)
        close.imageTintList = android.content.res.ColorStateList.valueOf(accent)
        close.setBackgroundResource(selectableBg())
        close.contentDescription = "Cerrar"
        close.setOnClickListener { host.close() }
        head.addView(close, LinearLayout.LayoutParams(dp(44), dp(44)))
        view.addView(head)

        val scroll = HorizontalScrollView(act)
        scroll.isHorizontalScrollBarEnabled = false
        val bar = LinearLayout(act)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.setPadding(dp(12), dp(4), dp(12), dp(8))
        bar.addView(chip("Texto propio", R.drawable.ic_edit) { editor(null, insertNow = true) }, chipLp())
        bar.addView(chip("Agregar mensaje", R.drawable.ic_add) { editor(null, insertNow = false) }, chipLp())
        scroll.addView(bar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        view.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val sv = ScrollView(act)
        column = LinearLayout(act)
        column.orientation = LinearLayout.VERTICAL
        column.setPadding(0, 0, 0, dp(12))
        sv.addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        view.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun selectableBg(): Int {
        val tv = android.util.TypedValue()
        act.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
        return tv.resourceId
    }

    private fun chipLp() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        marginEnd = dp(8)
    }

    private fun chip(label: String, icon: Int, onClick: () -> Unit): TextView {
        val tv = TextView(act)
        tv.text = label
        tv.textSize = 13f
        tv.setTextColor(onSurface)
        tv.gravity = Gravity.CENTER_VERTICAL
        tv.setPadding(dp(12), dp(8), dp(14), dp(8))
        AppCompatResources.getDrawable(act, icon)?.mutate()?.let {
            it.setTint(accent)
            it.setBounds(0, 0, dp(18), dp(18))
            tv.setCompoundDrawablesRelative(it, null, null, null)
        }
        tv.compoundDrawablePadding = dp(6)
        tv.background = GradientDrawable().apply {
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1), ColorUtils.setAlphaComponent(onSurface, 60))
            setColor(ColorUtils.setAlphaComponent(accent, 0x1A))
        }
        tv.setOnClickListener { onClick() }
        return tv
    }

    /** Al abrir el cajón: muestra la lista actual. */
    fun open() = render()

    private fun render() {
        column.removeAllViews()
        val list = host.snippets()
        if (list.isEmpty()) {
            val e = TextView(act)
            e.text = "Todavía no hay mensajes guardados. Tocá «Agregar mensaje» para crear uno."
            e.textSize = 14f
            e.setTextColor(muted)
            e.setPadding(dp(20), dp(24), dp(20), dp(24))
            column.addView(e)
            return
        }
        list.forEachIndexed { i, text ->
            val row = TextView(act)
            row.text = text
            row.textSize = 15f
            row.setTextColor(onSurface)
            row.maxLines = 3
            row.ellipsize = TextUtils.TruncateAt.END
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(16), dp(12), dp(16), dp(12))
            AppCompatResources.getDrawable(act, R.drawable.ic_message)?.mutate()?.let {
                it.setTint(accent)
                it.setBounds(0, 0, dp(24), dp(24))
                row.setCompoundDrawablesRelative(it, null, null, null)
            }
            row.compoundDrawablePadding = dp(16)
            row.setBackgroundResource(selectableBg())
            row.setOnClickListener {
                host.close()
                host.insert(text)
            }
            row.setOnLongClickListener {
                actions(i)
                true
            }
            column.addView(row)
        }
    }

    private fun actions(index: Int) {
        val list = host.snippets()
        val text = list.getOrNull(index) ?: return
        val items = mutableListOf("Escribir en el chat", "Editar")
        if (index > 0) items.add("Subir")
        items.add("Borrar")
        MaterialAlertDialogBuilder(act)
            .setTitle(text.take(60))
            .setItems(items.toTypedArray()) { _, which ->
                when (items[which]) {
                    "Escribir en el chat" -> {
                        host.close()
                        host.insert(text)
                    }
                    "Editar" -> editor(index, insertNow = false)
                    "Subir" -> {
                        val m = list.toMutableList()
                        m.add(index - 1, m.removeAt(index))
                        host.saveSnippets(m)
                        render()
                    }
                    else -> {
                        host.saveSnippets(list.toMutableList().also { it.removeAt(index) })
                        render()
                        host.message("Mensaje borrado")
                    }
                }
            }
            .show()
    }

    /** `index` null = nuevo. Con `insertNow` es el «texto propio»: se escribe en el chat y, si querés, se guarda. */
    private fun editor(index: Int?, insertNow: Boolean) {
        val list = host.snippets()
        val input = TextInputEditText(act)
        input.setText(if (index != null) list.getOrNull(index) ?: "" else "")
        input.minLines = 3
        input.maxLines = 8
        input.gravity = Gravity.TOP or Gravity.START
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        input.hint = "Escribí tu mensaje"
        val holder = FrameLayout(act)
        holder.setPadding(dp(20), dp(8), dp(20), 0)
        holder.addView(input, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val b = MaterialAlertDialogBuilder(act)
            .setTitle(if (insertNow) "Texto propio" else if (index == null) "Nuevo mensaje guardado" else "Editar mensaje")
            .setView(holder)
            .setNegativeButton("Cancelar", null)
        if (insertNow) {
            b.setPositiveButton("Escribir en el chat") { _, _ ->
                val t = input.text?.toString().orEmpty()
                if (t.isNotBlank()) {
                    host.close()
                    host.insert(t)
                }
            }
            b.setNeutralButton("Guardar y escribir") { _, _ ->
                val t = input.text?.toString().orEmpty()
                if (t.isNotBlank()) {
                    host.saveSnippets(list + t)
                    host.close()
                    host.insert(t)
                }
            }
        } else {
            b.setPositiveButton("Guardar") { _, _ ->
                val t = input.text?.toString().orEmpty()
                if (t.isBlank()) return@setPositiveButton
                host.saveSnippets(if (index == null) list + t else list.toMutableList().also { it[index] = t })
                render()
                host.message("Mensaje guardado")
            }
        }
        val dlg = b.show()
        input.requestFocus()
        dlg.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    }
}
