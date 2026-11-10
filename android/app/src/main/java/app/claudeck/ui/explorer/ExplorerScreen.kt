package app.claudeck.ui.explorer

import android.graphics.Bitmap
import android.text.format.DateFormat
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.claudeck.FileEntry
import app.claudeck.FileKind
import app.claudeck.FileOps
import app.claudeck.R
import app.claudeck.domain.usecase.ArchiveRules
import app.claudeck.domain.usecase.BarAction
import app.claudeck.domain.usecase.ExplorerRules
import app.claudeck.ui.theme.AlternTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Todo lo que la pantalla le pide al explorador. */
interface ExplorerActions {
    fun close()
    fun goUp()
    fun openFolder(dir: File)
    fun goDownloads()
    fun goStorage()
    fun itemClick(e: FileEntry)
    fun toggle(f: File)
    fun selectAll()
    fun clearSelection()
    fun setClip(files: List<File>, cut: Boolean)
    fun discardClip()
    fun paste()
    fun nextSort()
    fun finishPick()
    fun systemPicker()
    fun askPermission()
    fun openFile(f: File, asText: Boolean, chooser: Boolean)
    fun delete(files: List<File>)
    fun rename(f: File, name: String)
    fun newFolder(name: String)
    /** [inFolder] = true extrae dentro de una carpeta nueva con el nombre del zip. */
    fun extract(zip: File, inFolder: Boolean)
    /** Dentro de un .zip/.rar: extrae lo elegido en una carpeta junto al archivo. */
    fun extractEntries(files: List<File>)
    /** Entra en un .zip/.rar y muestra lo que tiene adentro como si fuera una carpeta. */
    fun openArchive(f: File)
    /** Miniatura de una imagen o video (puede tardar: se llama desde un hilo de fondo). */
    fun thumbnail(e: FileEntry, px: Int): Bitmap?
}

private sealed interface Dlg {
    data class Menu(val entry: FileEntry) : Dlg
    data class Delete(val files: List<File>) : Dlg
    data class Rename(val file: File) : Dlg
    object NewFolder : Dlg
    data class Zip(val file: File) : Dlg
}

private val DeleteRed = Color(0xFFE5484D)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExplorerScreen(
    state: ExplorerState, surface: Color, fg: Color, accent: Color, actions: ExplorerActions
) {
    val muted = fg.copy(alpha = 0.59f)
    var dlg by remember { mutableStateOf<Dlg?>(null) }
    val listState = rememberLazyListState()
    LaunchedEffect(state.scrollTop) { listState.scrollToItem(0) }

    state.zipPrompt?.let { z ->
        LaunchedEffect(z) {
            dlg = Dlg.Zip(z)
            state.zipPrompt = null
        }
    }

    val atRoot = state.atRoot()
    val only = state.selected.singleOrNull()

    val onlyIsZip = only != null && only.isFile && ArchiveRules.isArchive(only.name)
    val inArchive = state.archive != null
    val places = ExplorerRules.placeActions(state.hasAccess, inArchive)
    val menu = ExplorerRules.fabActions(
        state.hasAccess, state.picking, state.selected.size, onlyIsZip, state.clip.size,
        inArchive = inArchive, archiveWritable = state.archiveWritable
    )
    val perform: (BarAction) -> Unit = { a ->
        when (a) {
            BarAction.PICK_UPLOAD -> actions.finishPick()
            BarAction.DOWNLOADS -> actions.goDownloads()
            BarAction.STORAGE -> actions.goStorage()
            BarAction.SYSTEM_PICKER -> actions.systemPicker()
            BarAction.CANCEL -> actions.close()
            BarAction.COPY -> actions.setClip(state.selected.toList(), false)
            BarAction.MOVE -> actions.setClip(state.selected.toList(), true)
            BarAction.RENAME -> only?.let { dlg = Dlg.Rename(it) }
            BarAction.EXTRACT ->
                if (state.archive != null) actions.extractEntries(state.selected.toList())
                else only?.let { dlg = Dlg.Zip(it) }
            BarAction.EXTRACT_ALL -> state.archive?.let { actions.extract(it, true) }
            BarAction.DELETE -> if (state.selected.isNotEmpty()) dlg = Dlg.Delete(state.selected.toList())
            BarAction.SELECT_ALL -> actions.selectAll()
            BarAction.CANCEL_SELECTION -> actions.clearSelection()
            BarAction.PASTE -> actions.paste()
            BarAction.DISCARD_CLIP -> actions.discardClip()
            BarAction.NEW_FOLDER -> dlg = Dlg.NewFolder
            BarAction.SORT -> actions.nextSort()
        }
    }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        // Cabecera: subir · carpeta y ruta · cerrar
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp)) {
                if (!atRoot) {
                    Icon(
                        painterResource(R.drawable.ic_back), contentDescription = "Subir una carpeta", tint = accent,
                        modifier = Modifier.size(44.dp).clickable { actions.goUp() }.padding(10.dp)
                    )
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(
                    state.archive?.let { ExplorerRules.archiveTitle(it.name, state.inner) }
                        ?: ExplorerRules.title(state.picking, atRoot, state.dir.name),
                    color = fg, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    state.archive?.let { ExplorerRules.archivePath(it.name, state.inner) }
                        ?: ExplorerRules.relativePath(state.dir.path, state.storageRoot.path),
                    color = muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                painterResource(R.drawable.ic_close), contentDescription = "Cerrar el explorador", tint = accent,
                modifier = Modifier.size(44.dp).clickable { actions.close() }.padding(10.dp)
            )
        }

        // Barra fija: solo los lugares (Descargas y Almacenamiento) y, si hay selección, cuántos elementos.
        if (places.isNotEmpty() || (!state.picking && state.selected.isNotEmpty())) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!state.picking && state.selected.isNotEmpty()) {
                    Text(
                        ExplorerRules.selectionCount(state.selected.size),
                        color = muted, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, end = 12.dp)
                    )
                }
                for (a in places) {
                    Chip(ExplorerRules.barLabel(a, state.selected.size, state.clip.size, state.sort.label), barIcon(a), fg, accent) {
                        perform(a)
                    }
                }
            }
        }

        if (state.busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = accent)
        } else {
            Box(Modifier.fillMaxWidth().height(4.dp))
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                !state.hasAccess -> Column(
                    Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Para ver y administrar tus archivos, AlternAIs necesita acceso a los archivos del dispositivo.",
                        color = fg, fontSize = 15.sp, textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = { actions.askPermission() },
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White),
                        modifier = Modifier.padding(top = 16.dp)
                    ) { Text("Dar permiso") }
                }
                state.entries.isEmpty() -> Text(
                    if (state.unreadable) "No se puede leer esta carpeta" else "Carpeta vacía",
                    color = muted, fontSize = 15.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxSize().padding(top = 48.dp)
                )
                else -> LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = if (menu.isNotEmpty()) 88.dp else 12.dp)) {
                    items(state.entries, key = { it.file.path }) { e ->
                        val on = state.selected.contains(e.file)
                        Row(
                            Modifier.fillMaxWidth()
                                .background(if (on) accent.copy(alpha = 0.165f) else Color.Transparent)
                                .combinedClickable(
                                    onClick = { actions.itemClick(e) },
                                    onLongClick = {
                                        if (state.picking) { if (!e.isDir) actions.toggle(e.file) } else dlg = Dlg.Menu(e)
                                    }
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FileIcon(e, actions)
                            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                                Text(e.name, color = fg, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                val date = DateFormat.format("d MMM yyyy HH:mm", e.modified).toString()
                                Text(
                                    if (e.isDir) "Carpeta · $date" else FileOps.humanSize(e.size) + " · " + date,
                                    color = muted, fontSize = 12.sp, maxLines = 1
                                )
                            }
                            // Checkbox a la derecha de cada elemento para seleccionar varios a la vez
                            // (al subir archivos las carpetas no se pueden elegir).
                            if (!(state.picking && e.isDir)) {
                                Box(
                                    Modifier.padding(start = 4.dp).size(40.dp)
                                        .clickable { actions.toggle(e.file) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        Modifier.size(22.dp).clip(RoundedCornerShape(6.dp))
                                            .background(if (on) accent else Color.Transparent)
                                            .border(2.dp, if (on) accent else muted, RoundedCornerShape(6.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (on) Icon(
                                            painterResource(R.drawable.ic_check), null, tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
        if (menu.isNotEmpty()) {
            ExplorerFab(menu, state.selected.size, state.clip.size, state.sort.label, surface, fg, accent, perform)
        }
    }

    // ---- Diálogos
    val close = { dlg = null }
    when (val d = dlg) {
        null -> Unit
        is Dlg.Menu -> {
            val e = d.entry
            // Si el elemento está dentro de la selección, el menú actúa sobre toda la selección; si no, sobre él solo.
            val targets = if (state.selected.contains(e.file)) state.selected.toList() else listOf(e.file)
            val multi = targets.size > 1
            val ext = e.file.extension.lowercase()
            val inArc = state.archive != null
            val readOnly = inArc && !state.archiveWritable
            AlertDialog(
                onDismissRequest = close,
                containerColor = surface, titleContentColor = muted, textContentColor = fg,
                title = { Text(if (multi) "${targets.size} elementos" else e.name, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                text = {
                    Column {
                        if (!multi) {
                            if (e.isDir) {
                                MenuRow("Abrir", R.drawable.ic_folder, fg, accent) { close(); actions.openFolder(e.file) }
                            } else if (ext == "apk") {
                                MenuRow("Instalar", R.drawable.ic_download, fg, accent) { close(); actions.openFile(e.file, false, false) }
                            } else {
                                MenuRow("Abrir", R.drawable.ic_file, fg, accent) {
                                    close()
                                    if (e.isArchive && !inArc) actions.openArchive(e.file) else actions.openFile(e.file, false, true)
                                }
                            }
                            if (!e.isDir) {
                                MenuRow("Abrir con…", R.drawable.ic_forward, fg, accent) { close(); actions.openFile(e.file, false, true) }
                                MenuRow("Abrir como texto", R.drawable.ic_edit, fg, accent) { close(); actions.openFile(e.file, true, true) }
                            }
                            if (e.isArchive && !inArc) MenuRow("Extraer", R.drawable.ic_zip, fg, accent) { dlg = Dlg.Zip(e.file) }
                        }
                        if (!state.selected.contains(e.file)) MenuRow("Seleccionar", R.drawable.ic_check, fg, accent) { close(); actions.toggle(e.file) }
                        MenuRow("Copiar", R.drawable.ic_copy, fg, accent) { close(); actions.setClip(targets, false) }
                        if (!inArc) MenuRow("Mover", R.drawable.ic_move, fg, accent) { close(); actions.setClip(targets, true) }
                        if (inArc) MenuRow("Extraer", R.drawable.ic_zip, fg, accent) { close(); actions.extractEntries(targets) }
                        if (!multi && !readOnly) MenuRow("Renombrar", R.drawable.ic_edit, fg, accent) { dlg = Dlg.Rename(e.file) }
                        if (!readOnly) MenuRow("Borrar", R.drawable.ic_trash, fg, DeleteRed) { dlg = Dlg.Delete(targets) }
                    }
                },
                confirmButton = {}
            )
        }
        is Dlg.Delete -> AlertDialog(
            onDismissRequest = close,
            containerColor = surface, titleContentColor = fg, textContentColor = fg,
            title = { Text("Borrar") },
            text = { Text(ExplorerRules.deleteQuestion(d.files.size, d.files[0].name)) },
            confirmButton = { TextButton(onClick = { close(); actions.delete(d.files) }) { Text("Borrar", color = DeleteRed) } },
            dismissButton = { TextButton(onClick = close) { Text("Cancelar") } }
        )
        is Dlg.Rename -> NameDialog("Renombrar", d.file.name, "Renombrar", surface, fg, close) { name ->
            close(); actions.rename(d.file, name)
        }
        is Dlg.NewFolder -> NameDialog("Nueva carpeta", "", "Crear", surface, fg, close) { name ->
            close(); actions.newFolder(name)
        }
        is Dlg.Zip -> {
            val base = ExplorerRules.zipFolderName(d.file.name)
            AlertDialog(
                onDismissRequest = close,
                containerColor = surface, titleContentColor = fg, textContentColor = fg,
                title = { Text(d.file.name, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                text = {
                    Column {
                        OptionRow("Ver lo que tiene adentro", fg) { close(); actions.openArchive(d.file) }
                        OptionRow("Extraer en una carpeta «$base»", fg) { close(); actions.extract(d.file, true) }
                        OptionRow("Extraer aquí", fg) { close(); actions.extract(d.file, false) }
                        OptionRow("Abrir con otra app", fg) { close(); actions.openFile(d.file, false, true) }
                    }
                },
                confirmButton = {}
            )
        }
    }
}

@DrawableRes
private fun barIcon(a: BarAction): Int = when (a) {
    BarAction.PICK_UPLOAD -> R.drawable.ic_upload
    BarAction.DOWNLOADS, BarAction.STORAGE -> R.drawable.ic_folder
    BarAction.SYSTEM_PICKER -> R.drawable.ic_file
    BarAction.CANCEL, BarAction.CANCEL_SELECTION, BarAction.DISCARD_CLIP -> R.drawable.ic_close
    BarAction.COPY -> R.drawable.ic_copy
    BarAction.MOVE -> R.drawable.ic_move
    BarAction.RENAME -> R.drawable.ic_edit
    BarAction.EXTRACT, BarAction.EXTRACT_ALL -> R.drawable.ic_zip
    BarAction.DELETE -> R.drawable.ic_trash
    BarAction.SELECT_ALL -> R.drawable.ic_select_all
    BarAction.PASTE -> R.drawable.ic_paste
    BarAction.NEW_FOLDER -> R.drawable.ic_folder_plus
    BarAction.SORT -> R.drawable.ic_sort
}

/** Ícono por tipo; para imágenes y videos, la miniatura apenas esté lista. */
@Composable
private fun FileIcon(e: FileEntry, actions: ExplorerActions) {
    val thumbKind = !e.isDir && (e.kind == FileKind.IMAGE || e.kind == FileKind.VIDEO) &&
        !e.name.endsWith(".svg", ignoreCase = true)
    if (!thumbKind) {
        Icon(
            painterResource(if (e.isDir) R.drawable.ic_folder else if (e.kind == FileKind.ARCHIVE) R.drawable.ic_zip else R.drawable.ic_file),
            null, tint = Color(e.kind.color), modifier = Modifier.size(26.dp)
        )
        return
    }
    val thumb by produceState<Bitmap?>(null, e.file.absolutePath, e.modified) {
        value = withContext(Dispatchers.IO) { actions.thumbnail(e, 128) }
    }
    val shape = RoundedCornerShape(8.dp)
    val bmp = thumb
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(44.dp).clip(shape)
        )
    } else {
        Box(Modifier.size(44.dp).clip(shape), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_file), null, tint = Color(e.kind.color), modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun NameDialog(
    title: String, initial: String, positive: String, surface: Color, fg: Color,
    onDismiss: () -> Unit, onOk: (String) -> Unit
) {
    val dot = initial.lastIndexOf('.')
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, if (dot > 0) dot else initial.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = surface, titleContentColor = fg, textContentColor = fg,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value, onValueChange = { value = it }, singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focus)
            )
        },
        confirmButton = { TextButton(enabled = value.text.isNotBlank(), onClick = { onOk(value.text) }) { Text(positive) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun Chip(label: String, @DrawableRes icon: Int, fg: Color, accent: Color, onClick: () -> Unit) {
    Row(
        Modifier.padding(end = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(accent.copy(alpha = 0.10f))
            .border(BorderStroke(1.dp, fg.copy(alpha = 0.24f)), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(painterResource(icon), null, tint = accent, modifier = Modifier.size(18.dp))
        Text(label, color = fg, fontSize = 13.sp)
    }
}

/** Botón flotante abajo a la derecha: abre el menú con todas las acciones (en modo «subir» agrega «Subir (n)» a la vista). */
@Composable
private fun BoxScope.ExplorerFab(
    menu: List<BarAction>, selectedCount: Int, clipCount: Int, sortLabel: String,
    surface: Color, fg: Color, accent: Color, perform: (BarAction) -> Unit
) {
    var open by remember(menu) { mutableStateOf(false) }
    val upload = menu.firstOrNull { it == BarAction.PICK_UPLOAD }
    val items = menu.filter { it != BarAction.PICK_UPLOAD }

    if (open) {
        // Tocar fuera del menú lo cierra.
        Box(
            Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { open = false }
        )
    }
    Column(
        Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (open && items.isNotEmpty()) {
            Column(
                Modifier.shadow(8.dp, RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp))
                    .background(surface)
                    .border(BorderStroke(1.dp, fg.copy(alpha = 0.24f)), RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                for (a in items) {
                    MenuRow(
                        ExplorerRules.barLabel(a, selectedCount, clipCount, sortLabel), barIcon(a), fg,
                        if (a == BarAction.DELETE) DeleteRed else accent
                    ) { open = false; perform(a) }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (upload != null) {
                Row(
                    Modifier.shadow(6.dp, RoundedCornerShape(28.dp)).clip(RoundedCornerShape(28.dp)).background(accent)
                        .clickable { perform(upload) }.padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(painterResource(R.drawable.ic_upload), null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Text(ExplorerRules.barLabel(upload, selectedCount, clipCount, sortLabel), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (items.isNotEmpty()) {
                Box(
                    Modifier.size(56.dp).shadow(6.dp, CircleShape).clip(CircleShape).background(accent)
                        .clickable { open = !open },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painterResource(if (open) R.drawable.ic_close else R.drawable.ic_more),
                        contentDescription = if (open) "Cerrar opciones" else "Opciones",
                        tint = Color.White, modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuRow(label: String, @DrawableRes icon: Int, fg: Color, tint: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(onClick = onClick).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(painterResource(icon), null, tint = tint, modifier = Modifier.size(20.dp))
        Text(label, color = fg, fontSize = 15.sp)
    }
}

@Composable
private fun OptionRow(label: String, fg: Color, onClick: () -> Unit) {
    Text(
        label, color = fg, fontSize = 16.sp,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick).padding(vertical = 12.dp)
    )
}

/** Vista de Compose con el explorador; el marco (borde, esquinas, animación) sigue siendo de `FileExplorer`. */
fun createExplorerView(
    context: android.content.Context, state: ExplorerState, actions: ExplorerActions, surface: Int, onSurface: Int, accent: Int
): androidx.compose.ui.platform.ComposeView = androidx.compose.ui.platform.ComposeView(context).apply {
    setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        AlternTheme(dark = androidx.core.graphics.ColorUtils.calculateLuminance(surface) < 0.5) {
            ExplorerScreen(state, Color(surface), Color(onSurface), Color(app.claudeck.ui.theme.AccentState.color), actions)
        }
    }
}
