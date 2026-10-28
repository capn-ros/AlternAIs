package app.claudeck

import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

/**
 * Selector de carpeta simple: muestra la carpeta actual y deja entrar en subcarpetas, subir, crear una
 * carpeta nueva o elegir la actual. Necesita el acceso a todos los archivos (el mismo del explorador).
 */
object FolderPicker {
    fun show(act: AppCompatActivity, root: File, start: File, title: String, onPick: (File) -> Unit) {
        val dir = if (start.isDirectory && FileOps.isInside(start, root)) start else root
        val subs = (dir.listFiles() ?: emptyArray())
            .filter { it.isDirectory && !it.name.startsWith(".") }
            .sortedBy { it.name.lowercase() }
        val atRoot = dir.canonicalPath == root.canonicalPath

        val labels = ArrayList<String>()
        val actions = ArrayList<() -> Unit>()
        labels.add("✔  Guardar en esta carpeta")
        actions.add { onPick(dir) }
        if (!atRoot) {
            labels.add("↑  Subir una carpeta")
            actions.add { show(act, root, dir.parentFile ?: root, title, onPick) }
        }
        labels.add("＋  Carpeta nueva")
        actions.add { newFolder(act, root, dir, title, onPick) }
        for (s in subs) {
            labels.add("📁  " + s.name)
            actions.add { show(act, root, s, title, onPick) }
        }

        val rel = dir.path.removePrefix(root.path).ifEmpty { "/" }
        MaterialAlertDialogBuilder(act)
            .setTitle(title)
            .setMessage(rel)
            .setItems(labels.toTypedArray()) { _, which -> actions[which]() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun newFolder(act: AppCompatActivity, root: File, dir: File, title: String, onPick: (File) -> Unit) {
        val input = com.google.android.material.textfield.TextInputEditText(act)
        input.setSingleLine(true)
        val pad = (20 * act.resources.displayMetrics.density).toInt()
        val holder = android.widget.FrameLayout(act)
        holder.setPadding(pad, pad / 2, pad, 0)
        holder.addView(input)
        MaterialAlertDialogBuilder(act)
            .setTitle("Carpeta nueva")
            .setView(holder)
            .setPositiveButton("Crear") { _, _ ->
                val name = input.text?.toString().orEmpty().trim()
                if (name.isNotEmpty() && !name.contains('/') && !name.contains('\\')) {
                    val f = File(dir, name)
                    f.mkdirs()
                    show(act, root, if (f.isDirectory) f else dir, title, onPick)
                } else {
                    show(act, root, dir, title, onPick)
                }
            }
            .setNegativeButton("Cancelar") { _, _ -> show(act, root, dir, title, onPick) }
            .show()
    }
}
