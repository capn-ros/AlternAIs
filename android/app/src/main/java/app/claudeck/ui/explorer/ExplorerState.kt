package app.claudeck.ui.explorer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.claudeck.FileEntry
import app.claudeck.SortMode
import java.io.File

/** Lo que muestra el explorador. Lo escribe `FileExplorer` y lo lee la pantalla de Compose. */
class ExplorerState(val storageRoot: File, val downloads: File, startDir: File) {
    var dir by mutableStateOf(startDir)
    var sort by mutableStateOf(SortMode.DATE)
    var entries by mutableStateOf<List<FileEntry>>(emptyList())
    var selected by mutableStateOf<Set<File>>(emptySet())
    var clip by mutableStateOf<List<File>>(emptyList())
    var hasAccess by mutableStateOf(true)
    var unreadable by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var picking by mutableStateOf(false)
    /** Se suma cada vez que hay que volver al principio de la lista (al entrar en otra carpeta). */
    var scrollTop by mutableStateOf(0)
    /** Un toque en un .zip pide mostrar sus opciones; la pantalla lo atiende y lo limpia. */
    var zipPrompt by mutableStateOf<File?>(null)

    /** Si no es null, se está mirando por dentro de este .zip/.rar; [inner] es la carpeta interna («» = raíz). */
    var archive by mutableStateOf<File?>(null)
    var inner by mutableStateOf("")
    var archiveWritable by mutableStateOf(false)

    fun atRoot(): Boolean = archive == null && dir.canonicalPath == storageRoot.canonicalPath
}
