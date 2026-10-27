package app.claudeck

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.text.TextUtils
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import java.io.File

/** Lo que el explorador le pide a la pantalla principal. */
interface ExplorerHost {
    fun surface(): Int
    fun onSurface(): Int
    fun accent(): Int
    fun border(): Int
    fun close()
    fun message(text: String)
    fun requestLegacyPermission()
}

/**
 * Explorador de archivos interno: un cajón grande con la estética de la app. Permite entrar en carpetas,
 * abrir archivos, seleccionar varios (mantener apretado), copiar, mover, renombrar, borrar, crear
 * carpetas y extraer .zip. Se arma entero en código; `view` se agrega al contenedor de la pantalla.
 */
class FileExplorer(private val act: AppCompatActivity, private val host: ExplorerHost) {
    val view: LinearLayout = LinearLayout(act)

    private val storageRoot: File = Environment.getExternalStorageDirectory()
    private val downloads: File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    private var dir: File = if (downloads.exists()) downloads else storageRoot
    private var sort = SortMode.DATE
    private var entries: List<FileEntry> = emptyList()
    private val selected = LinkedHashSet<File>()
    private var clip: List<File> = emptyList()
    private var clipCut = false

    private val onSurface = host.onSurface()
    private val muted = ColorUtils.setAlphaComponent(host.onSurface(), 150)
    private val accent = host.accent()

    private lateinit var upButton: ImageButton
    private lateinit var titleView: TextView
    private lateinit var pathView: TextView
    private lateinit var bar: LinearLayout
    private lateinit var progress: LinearProgressIndicator
    private lateinit var list: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var permView: LinearLayout
    private val adapter = Adapter()

    init {
        build()
    }

    private fun dp(v: Int): Int = (v * act.resources.displayMetrics.density).toInt()

    // ---------------------------------------------------------------- Construcción

    private fun build() {
        val r = dp(22).toFloat()
        view.orientation = LinearLayout.VERTICAL
        view.elevation = dp(8).toFloat()
        view.isClickable = true // que los toques no pasen al WebView de atrás
        view.visibility = View.GONE
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            setColor(host.surface())
            setStroke(dp(3), host.border())
        }
        view.clipToOutline = true
        view.setPadding(0, dp(10), 0, 0)

        // Cabecera: subir · nombre de la carpeta y ruta · cerrar
        val head = LinearLayout(act)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(dp(8), 0, dp(8), dp(4))
        upButton = iconButton(R.drawable.ic_back, "Subir una carpeta") { goUp() }
        head.addView(upButton, LinearLayout.LayoutParams(dp(44), dp(44)))
        val texts = LinearLayout(act)
        texts.orientation = LinearLayout.VERTICAL
        titleView = TextView(act)
        titleView.textSize = 18f
        titleView.setTypeface(titleView.typeface, android.graphics.Typeface.BOLD)
        titleView.setTextColor(onSurface)
        titleView.maxLines = 1
        titleView.ellipsize = TextUtils.TruncateAt.END
        pathView = TextView(act)
        pathView.textSize = 12f
        pathView.setTextColor(muted)
        pathView.maxLines = 1
        pathView.ellipsize = TextUtils.TruncateAt.START
        texts.addView(titleView)
        texts.addView(pathView)
        head.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(4)
            marginEnd = dp(4)
        })
        head.addView(iconButton(R.drawable.ic_close, "Cerrar el explorador") { host.close() }, LinearLayout.LayoutParams(dp(44), dp(44)))
        view.addView(head)

        // Barra de acciones (cambia si hay selección).
        val scroll = HorizontalScrollView(act)
        scroll.isHorizontalScrollBarEnabled = false
        bar = LinearLayout(act)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.setPadding(dp(12), dp(4), dp(12), dp(8))
        scroll.addView(bar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        view.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        progress = LinearProgressIndicator(act)
        progress.isIndeterminate = true
        progress.setIndicatorColor(accent)
        progress.visibility = View.INVISIBLE
        view.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // Lista + estados vacío / sin permiso.
        val body = FrameLayout(act)
        list = RecyclerView(act)
        list.layoutManager = LinearLayoutManager(act)
        list.adapter = adapter
        list.clipToPadding = false
        list.setPadding(0, 0, 0, dp(12))
        body.addView(list, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        emptyView = TextView(act)
        emptyView.text = "Carpeta vacía"
        emptyView.textSize = 15f
        emptyView.setTextColor(muted)
        emptyView.gravity = Gravity.CENTER
        emptyView.visibility = View.GONE
        body.addView(emptyView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        permView = LinearLayout(act)
        permView.orientation = LinearLayout.VERTICAL
        permView.gravity = Gravity.CENTER
        permView.setPadding(dp(32), dp(16), dp(32), dp(16))
        permView.visibility = View.GONE
        val permText = TextView(act)
        permText.text = "Para ver y administrar tus archivos, AlternAIs necesita acceso a los archivos del dispositivo."
        permText.textSize = 15f
        permText.setTextColor(onSurface)
        permText.gravity = Gravity.CENTER
        permView.addView(permText)
        val permBtn = MaterialButton(act)
        permBtn.text = "Dar permiso"
        permBtn.setOnClickListener { askPermission() }
        permView.addView(permBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(16)
        })
        body.addView(permView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        view.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun iconButton(icon: Int, desc: String, onClick: () -> Unit): ImageButton {
        val b = ImageButton(act)
        b.setImageResource(icon)
        b.imageTintList = android.content.res.ColorStateList.valueOf(accent)
        b.setBackgroundResource(selectableBg())
        b.contentDescription = desc
        b.setOnClickListener { onClick() }
        return b
    }

    private fun selectableBg(): Int {
        val tv = android.util.TypedValue()
        act.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
        return tv.resourceId
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

    private fun addChip(c: TextView) {
        bar.addView(c, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginEnd = dp(8)
        })
    }

    // ---------------------------------------------------------------- Permiso

    private fun hasAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else ContextCompat.checkSelfPermission(act, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private fun askPermission() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                act.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${act.packageName}")))
            } catch (_: Exception) {
                try {
                    act.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                } catch (_: Exception) {
                    host.message("Activá el acceso a todos los archivos para AlternAIs en los ajustes del sistema")
                }
            }
        } else {
            host.requestLegacyPermission()
        }
    }

    // ---------------------------------------------------------------- Navegación

    /** Al abrir el cajón: muestra la carpeta actual (Descargas la primera vez). */
    fun open() = refresh()

    fun refresh() {
        if (!hasAccess()) {
            entries = emptyList()
            adapter.notifyDataSetChanged()
            permView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
            list.visibility = View.GONE
            renderHeader()
            renderBar()
            return
        }
        permView.visibility = View.GONE
        list.visibility = View.VISIBLE
        val l = FileOps.list(dir, sort)
        if (l == null) {
            entries = emptyList()
            emptyView.text = "No se puede leer esta carpeta"
        } else {
            entries = l
            emptyView.text = "Carpeta vacía"
        }
        selected.retainAll(entries.map { it.file }.toSet())
        emptyView.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        adapter.notifyDataSetChanged()
        renderHeader()
        renderBar()
    }

    private fun atRoot(): Boolean = dir.canonicalPath == storageRoot.canonicalPath

    private fun navigate(d: File) {
        dir = d
        selected.clear()
        refresh()
        list.scrollToPosition(0)
    }

    private fun goUp() {
        if (!atRoot()) dir.parentFile?.let { navigate(it) }
    }

    /** Atrás: sale de la selección o sube una carpeta. Devuelve false si no hay nada más que hacer (cerrar). */
    fun onBack(): Boolean {
        if (selected.isNotEmpty()) {
            selected.clear()
            adapter.notifyDataSetChanged()
            renderBar()
            return true
        }
        if (!atRoot() && hasAccess()) {
            goUp()
            return true
        }
        return false
    }

    private fun renderHeader() {
        val root = atRoot()
        titleView.text = if (root) "Almacenamiento" else dir.name
        val rel = dir.path.removePrefix(storageRoot.path).ifEmpty { "/" }
        pathView.text = rel
        upButton.visibility = if (root) View.INVISIBLE else View.VISIBLE
    }

    private fun renderBar() {
        bar.removeAllViews()
        if (!hasAccess()) return
        if (selected.isNotEmpty()) {
            val one = selected.size == 1
            val only = selected.firstOrNull()
            val count = TextView(act)
            count.text = if (one) "1 elemento" else "${selected.size} elementos"
            count.textSize = 13f
            count.setTextColor(muted)
            count.gravity = Gravity.CENTER_VERTICAL
            count.setPadding(dp(4), 0, dp(12), 0)
            bar.addView(count, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addChip(chip("Copiar", R.drawable.ic_copy) { setClip(false) })
            addChip(chip("Mover", R.drawable.ic_move) { setClip(true) })
            if (one) addChip(chip("Renombrar", R.drawable.ic_edit) { only?.let { renameDialog(it) } })
            if (one && only != null && only.isFile && only.name.lowercase().endsWith(".zip")) {
                addChip(chip("Extraer", R.drawable.ic_zip) { zipOptions(only) })
            }
            addChip(chip("Borrar", R.drawable.ic_trash) { deleteDialog(selected.toList()) })
            addChip(chip("Todo", R.drawable.ic_select_all) {
                selected.clear()
                selected.addAll(entries.map { it.file })
                adapter.notifyDataSetChanged()
                renderBar()
            })
            addChip(chip("Cancelar", R.drawable.ic_close) { onBack() })
            return
        }
        if (clip.isNotEmpty()) {
            addChip(chip("Pegar aquí (${clip.size})", R.drawable.ic_paste) { paste() })
            addChip(chip("Descartar", R.drawable.ic_close) {
                clip = emptyList()
                renderBar()
            })
        }
        addChip(chip("Descargas", R.drawable.ic_folder) { if (downloads.exists()) navigate(downloads) })
        addChip(chip("Almacenamiento", R.drawable.ic_folder) { navigate(storageRoot) })
        addChip(chip("Nueva carpeta", R.drawable.ic_folder_plus) { newFolderDialog() })
        addChip(chip(sort.label, R.drawable.ic_sort) {
            sort = sort.next()
            refresh()
        })
    }

    // ---------------------------------------------------------------- Acciones

    private fun setClip(cut: Boolean) {
        clip = selected.toList()
        clipCut = cut
        selected.clear()
        adapter.notifyDataSetChanged()
        renderBar()
        host.message((if (cut) "Para mover: " else "Para copiar: ") + clip.size + ". Andá a la carpeta destino y tocá «Pegar aquí»")
    }

    private fun busy(on: Boolean) {
        progress.visibility = if (on) View.VISIBLE else View.INVISIBLE
    }

    private fun runAsync(work: () -> String) {
        busy(true)
        Thread {
            val text = try {
                work()
            } catch (e: Exception) {
                "No se pudo: " + (e.message ?: e.javaClass.simpleName)
            }
            act.runOnUiThread {
                busy(false)
                selected.clear()
                refresh()
                host.message(text)
            }
        }.start()
    }

    private fun paste() {
        val files = clip
        val cut = clipCut
        val target = dir
        if (cut) clip = emptyList()
        runAsync {
            var n = 0
            for (f in files) {
                if (cut) FileOps.move(f, target) else FileOps.copy(f, target)
                n++
            }
            (if (cut) "Movidos: " else "Copiados: ") + n
        }
    }

    private fun deleteDialog(files: List<File>) {
        val what = if (files.size == 1) "«${files[0].name}»" else "${files.size} elementos"
        MaterialAlertDialogBuilder(act)
            .setTitle("Borrar")
            .setMessage("Se borra $what para siempre. No se puede deshacer.")
            .setPositiveButton("Borrar") { _, _ ->
                runAsync {
                    var n = 0
                    for (f in files) {
                        if (f.deleteRecursively()) n++
                    }
                    if (n == files.size) "Borrados: $n" else "Se borraron $n de ${files.size}"
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun textDialog(title: String, initial: String, positive: String, onOk: (String) -> Unit) {
        val input = TextInputEditText(act)
        input.setText(initial)
        input.setSingleLine(true)
        val dot = initial.lastIndexOf('.')
        input.setSelection(0, if (dot > 0) dot else initial.length)
        val holder = FrameLayout(act)
        holder.setPadding(dp(20), dp(8), dp(20), 0)
        holder.addView(input, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val dlg = MaterialAlertDialogBuilder(act)
            .setTitle(title)
            .setView(holder)
            .setPositiveButton(positive) { _, _ -> onOk(input.text?.toString().orEmpty()) }
            .setNegativeButton("Cancelar", null)
            .show()
        input.requestFocus()
        dlg.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    }

    private fun renameDialog(f: File) {
        textDialog("Renombrar", f.name, "Renombrar") { name ->
            runAsync {
                val n = FileOps.rename(f, name)
                "Ahora se llama «${n.name}»"
            }
        }
    }

    private fun newFolderDialog() {
        textDialog("Nueva carpeta", "", "Crear") { name ->
            val parent = dir
            runAsync {
                val n = FileOps.mkdir(parent, name)
                "Carpeta «${n.name}» creada"
            }
        }
    }

    private fun zipOptions(zip: File) {
        val base = zip.name.removeSuffix(".zip").removeSuffix(".ZIP")
        MaterialAlertDialogBuilder(act)
            .setTitle(zip.name)
            .setItems(arrayOf("Extraer en una carpeta «$base»", "Extraer aquí", "Abrir con otra app")) { _, which ->
                when (which) {
                    0 -> extract(zip, FileOps.uniqueFile(zip.parentFile ?: dir, base))
                    1 -> extract(zip, zip.parentFile ?: dir)
                    else -> openFile(zip)
                }
            }
            .show()
    }

    private fun extract(zip: File, dest: File) {
        runAsync {
            val n = FileOps.unzip(zip, dest)
            "Extraídos $n archivos en «${dest.name}»"
        }
    }

    private fun openFile(f: File) {
        try {
            val uri = FileProvider.getUriForFile(act, act.packageName + ".files", f)
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"
            val i = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            act.startActivity(Intent.createChooser(i, f.name))
        } catch (_: ActivityNotFoundException) {
            host.message("No hay una app para abrir este archivo")
        } catch (e: Exception) {
            host.message("No se pudo abrir: " + (e.message ?: ""))
        }
    }

    private fun onItemClick(e: FileEntry) {
        if (selected.isNotEmpty()) {
            toggle(e.file)
            return
        }
        when {
            e.isDir -> navigate(e.file)
            e.isZip -> zipOptions(e.file)
            else -> openFile(e.file)
        }
    }

    private fun toggle(f: File) {
        if (!selected.remove(f)) selected.add(f)
        adapter.notifyDataSetChanged()
        renderBar()
    }

    // ---------------------------------------------------------------- Lista

    private inner class Holder(val row: LinearLayout, val icon: ImageView, val name: TextView, val sub: TextView, val check: ImageView) :
        RecyclerView.ViewHolder(row)

    private inner class Adapter : RecyclerView.Adapter<Holder>() {
        override fun getItemCount(): Int = entries.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val row = LinearLayout(act)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(16), dp(8), dp(16), dp(8))
            row.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            val icon = ImageView(act)
            val texts = LinearLayout(act)
            texts.orientation = LinearLayout.VERTICAL
            val name = TextView(act)
            name.textSize = 15f
            name.setTextColor(onSurface)
            name.maxLines = 2
            name.ellipsize = TextUtils.TruncateAt.MIDDLE
            val sub = TextView(act)
            sub.textSize = 12f
            sub.setTextColor(muted)
            sub.maxLines = 1
            texts.addView(name)
            texts.addView(sub)
            val check = ImageView(act)
            check.setImageResource(R.drawable.ic_check)
            check.imageTintList = android.content.res.ColorStateList.valueOf(accent)
            row.addView(icon, LinearLayout.LayoutParams(dp(26), dp(26)).apply { marginEnd = dp(16) })
            row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(check, LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginStart = dp(8) })
            return Holder(row, icon, name, sub, check)
        }

        override fun onBindViewHolder(h: Holder, position: Int) {
            val e = entries[position]
            val on = selected.contains(e.file)
            h.icon.setImageResource(if (e.isDir) R.drawable.ic_folder else if (e.isZip) R.drawable.ic_zip else R.drawable.ic_file)
            h.icon.imageTintList = android.content.res.ColorStateList.valueOf(accent)
            h.name.text = e.name
            val date = DateFormat.format("d MMM yyyy HH:mm", e.modified).toString()
            h.sub.text = if (e.isDir) "Carpeta · $date" else FileOps.humanSize(e.size) + " · " + date
            h.check.visibility = if (on) View.VISIBLE else View.INVISIBLE
            h.row.setBackgroundColor(if (on) ColorUtils.setAlphaComponent(accent, 0x2A) else Color.TRANSPARENT)
            h.row.setOnClickListener { onItemClick(e) }
            h.row.setOnLongClickListener {
                toggle(e.file)
                true
            }
        }
    }
}
