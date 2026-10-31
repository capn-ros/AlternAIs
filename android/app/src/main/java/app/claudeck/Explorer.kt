package app.claudeck

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import app.claudeck.domain.usecase.BackAction
import app.claudeck.domain.usecase.ExplorerRules
import app.claudeck.domain.usecase.ItemAction
import app.claudeck.domain.usecase.PickFilter
import app.claudeck.ui.explorer.ExplorerActions
import app.claudeck.ui.explorer.ExplorerState
import app.claudeck.ui.explorer.createExplorerView
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

    /** Desde el modo «subir archivo»: volver al selector de archivos del sistema. */
    fun systemPicker()
}

/**
 * Explorador de archivos interno. El marco (borde de color de la cuenta, esquinas, animaciones y gesto de
 * cerrar) sigue siendo una vista de Android; el contenido es Compose (`ui/explorer`). Esta clase maneja
 * el estado y las operaciones sobre archivos; las reglas puras están en `ExplorerRules` (con tests).
 */
class FileExplorer(private val act: AppCompatActivity, private val host: ExplorerHost) {
    val view: LinearLayout = LinearLayout(act)

    private val storageRoot: File = Environment.getExternalStorageDirectory()
    private val downloads: File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    private val state = ExplorerState(storageRoot, downloads, if (downloads.exists()) downloads else storageRoot)
    private var clipCut = false

    /** Modo «subir archivo»: una página pidió adjuntar archivos y se eligen acá (varios a la vez). */
    private class PickRequest(val accept: List<String>, val onDone: (List<File>?) -> Unit)
    private var pick: PickRequest? = null

    /** Cambia el color del borde del cajón (sigue a la cuenta activa). */
    fun setBorder(color: Int) {
        (view.background as? GradientDrawable)?.setStroke(dp(3), color)
    }

    private fun dp(v: Int): Int = (v * act.resources.displayMetrics.density).toInt()

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

    // ---------------------------------------------------------------- Navegación y modo «subir»

    /** Al abrir el cajón: muestra la carpeta actual (Descargas la primera vez). */
    fun open() = refresh()

    fun isPicking(): Boolean = pick != null

    fun startPick(accept: List<String>, onDone: (List<File>?) -> Unit) {
        pick?.onDone?.invoke(null)
        pick = PickRequest(accept.filter { it.isNotBlank() }, onDone)
        state.picking = true
        state.selected = emptySet()
        refresh()
    }

    /** Se cerró el explorador (o se cambió de cajón) sin elegir: la página recibe «ningún archivo». */
    fun cancelPick() {
        val p = pick ?: return
        pick = null
        state.picking = false
        state.selected = emptySet()
        p.onDone(null)
        refresh()
    }

    /** Suelta el pedido sin avisar a la página (la va a atender el selector del sistema). */
    fun dropPick() {
        pick = null
        state.picking = false
        state.selected = emptySet()
        refresh()
    }

    private fun finishPick() {
        val p = pick ?: return
        val files = state.selected.toList()
        pick = null
        state.picking = false
        state.selected = emptySet()
        p.onDone(files)
        host.close()
    }

    private fun acceptable(e: FileEntry): Boolean {
        val acc = pick?.accept ?: return true
        return PickFilter.accepts(acc, e.file.name, e.isDir) { ext -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "" }
    }

    fun refresh() {
        state.hasAccess = hasAccess()
        if (!state.hasAccess) {
            state.entries = emptyList()
            state.unreadable = false
            return
        }
        val l = FileOps.list(state.dir, state.sort)
        state.unreadable = l == null
        state.entries = when {
            l == null -> emptyList()
            pick != null -> l.filter { acceptable(it) }
            else -> l
        }
        val present = state.entries.map { it.file }.toSet()
        state.selected = state.selected.filter { it in present }.toCollection(LinkedHashSet())
    }

    private fun navigate(d: File) {
        state.dir = d
        state.selected = emptySet()
        state.scrollTop++
        refresh()
    }

    private fun goUp() {
        if (!state.atRoot()) state.dir.parentFile?.let { navigate(it) }
    }

    /** Atrás: sale de la selección o sube una carpeta. Devuelve false si no hay nada más que hacer (cerrar). */
    fun onBack(): Boolean = when (ExplorerRules.back(state.selected.isNotEmpty(), state.atRoot(), hasAccess())) {
        BackAction.CLEAR_SELECTION -> {
            state.selected = emptySet()
            true
        }
        BackAction.GO_UP -> {
            goUp()
            true
        }
        BackAction.CLOSE -> false
    }

    // ---------------------------------------------------------------- Operaciones

    private fun runAsync(work: () -> String) {
        state.busy = true
        Thread {
            val text = try {
                work()
            } catch (e: Exception) {
                ExplorerRules.failure(e.message, e.javaClass.simpleName)
            }
            act.runOnUiThread {
                state.busy = false
                state.selected = emptySet()
                refresh()
                host.message(text)
            }
        }.start()
    }

    /** Abre el archivo con la app que corresponda (o instala un .apk). `asText` lo fuerza como texto; `chooser` deja elegir app. */
    private fun openFile(f: File, asText: Boolean = false, chooser: Boolean = true) {
        try {
            val uri = FileProvider.getUriForFile(act, act.packageName + ".files", f)
            val ext = f.extension.lowercase()
            val mime = when {
                asText -> "text/plain"
                ext == "apk" -> "application/vnd.android.package-archive"
                else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
            }
            val i = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            act.startActivity(if (chooser && ext != "apk") Intent.createChooser(i, f.name) else i)
        } catch (_: ActivityNotFoundException) {
            host.message("No hay una app para abrir este archivo")
        } catch (e: Exception) {
            host.message("No se pudo abrir: " + (e.message ?: ""))
        }
    }

    // ---------------------------------------------------------------- Miniaturas (a tamaño chico, en memoria)

    private val thumbCache = object : android.util.LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 12).toInt().coerceAtMost(48 * 1024 * 1024)
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private fun loadThumb(f: File, kind: FileKind, px: Int): Bitmap? = try {
        if (kind == FileKind.VIDEO) {
            if (Build.VERSION.SDK_INT >= 29) android.media.ThumbnailUtils.createVideoThumbnail(f, android.util.Size(px, px), null) else null
        } else {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.absolutePath, o)
            var sample = 1
            val big = maxOf(o.outWidth, o.outHeight)
            while (big / (sample * 2) >= px) sample *= 2
            val bmp = BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            if (bmp == null) null else {
                val rot = when (android.media.ExifInterface(f.absolutePath).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
                    6 -> 90f
                    3 -> 180f
                    8 -> 270f
                    else -> 0f
                }
                if (rot == 0f) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rot) }, true)
            }
        }
    } catch (_: Throwable) {
        null
    }

    // ---------------------------------------------------------------- Lo que pide la pantalla

    private val actions = object : ExplorerActions {
        override fun close() = host.close()
        override fun goUp() = this@FileExplorer.goUp()
        override fun openFolder(dir: File) = navigate(dir)
        override fun goDownloads() { if (downloads.exists()) navigate(downloads) }
        override fun goStorage() = navigate(storageRoot)

        override fun itemClick(e: FileEntry) {
            when (ExplorerRules.itemAction(pick != null, state.selected.isNotEmpty(), e.isDir, e.isZip)) {
                ItemAction.OPEN_FOLDER -> navigate(e.file)
                ItemAction.TOGGLE_SELECTION -> toggle(e.file)
                // La pantalla muestra el diálogo del zip con un toque largo; con un toque simple se abre con otra app.
                ItemAction.ZIP_OPTIONS -> zipTap(e.file)
                ItemAction.OPEN_FILE -> this@FileExplorer.openFile(e.file)
            }
        }

        override fun toggle(f: File) {
            val s = LinkedHashSet(state.selected)
            if (!s.remove(f)) s.add(f)
            state.selected = s
        }

        override fun selectAll() {
            state.selected = state.entries.map { it.file }.toCollection(LinkedHashSet())
        }

        override fun clearSelection() {
            state.selected = emptySet()
        }

        override fun setClip(files: List<File>, cut: Boolean) {
            state.clip = files
            clipCut = cut
            state.selected = emptySet()
            host.message(ExplorerRules.clipHint(cut, files.size))
        }

        override fun discardClip() {
            state.clip = emptyList()
        }

        override fun paste() {
            val files = state.clip
            val cut = clipCut
            val target = state.dir
            if (cut) state.clip = emptyList()
            runAsync {
                var n = 0
                for (f in files) {
                    if (cut) FileOps.move(f, target) else FileOps.copy(f, target)
                    n++
                }
                ExplorerRules.pasteResult(cut, n)
            }
        }

        override fun nextSort() {
            state.sort = state.sort.next()
            refresh()
        }

        override fun finishPick() = this@FileExplorer.finishPick()
        override fun systemPicker() = host.systemPicker()
        override fun askPermission() = this@FileExplorer.askPermission()
        override fun openFile(f: File, asText: Boolean, chooser: Boolean) = this@FileExplorer.openFile(f, asText, chooser)

        override fun delete(files: List<File>) {
            runAsync {
                var n = 0
                for (f in files) {
                    if (f.deleteRecursively()) n++
                }
                ExplorerRules.deleteResult(n, files.size)
            }
        }

        override fun rename(f: File, name: String) {
            runAsync {
                val n = FileOps.rename(f, name)
                ExplorerRules.renamed(n.name)
            }
        }

        override fun newFolder(name: String) {
            val parent = state.dir
            runAsync {
                val n = FileOps.mkdir(parent, name)
                ExplorerRules.folderCreated(n.name)
            }
        }

        override fun extract(zip: File, inFolder: Boolean) {
            val parent = zip.parentFile ?: state.dir
            val dest = if (inFolder) FileOps.uniqueFile(parent, ExplorerRules.zipFolderName(zip.name)) else parent
            runAsync {
                val n = FileOps.unzip(zip, dest)
                ExplorerRules.extracted(n, dest.name)
            }
        }

        override fun thumbnail(e: FileEntry, px: Int): Bitmap? {
            val key = e.file.absolutePath + "|" + e.modified
            thumbCache.get(key)?.let { return it }
            val b = loadThumb(e.file, e.kind, px) ?: return null
            thumbCache.put(key, b)
            return b
        }
    }

    /** Un toque en un .zip abre las opciones (extraer / abrir con otra app); lo pide la pantalla con un diálogo. */
    private fun zipTap(zip: File) {
        state.zipPrompt = zip
    }

    // El marco se arma al final: usa `actions`, que se inicializa arriba.
    init {
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
        view.addView(
            createExplorerView(act, state, actions, host.surface(), host.onSurface(), host.accent()),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )
    }
}
