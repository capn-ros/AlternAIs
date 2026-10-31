package app.claudeck.ui.snippets

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.claudeck.R
import app.claudeck.ui.theme.AlternTheme

/** Lo que el cajón le pide a la pantalla principal. */
interface SnippetsActions {
    fun close()
    fun insert(text: String)
    fun message(text: String)
}

fun createSnippetsView(
    context: Context, vm: SnippetsViewModel, actions: SnippetsActions, surface: Int, onSurface: Int, accent: Int
): ComposeView = ComposeView(context).apply {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        val list by vm.snippets.collectAsStateWithLifecycle()
        AlternTheme(dark = ColorUtils.calculateLuminance(surface) < 0.5) {
            SnippetsScreen(
                snippets = list, surface = Color(surface), fg = Color(onSurface), accent = Color(accent),
                onAdd = vm::add, onReplace = vm::replace, onMoveUp = vm::moveUp, onRemove = vm::remove,
                actions = actions
            )
        }
    }
}

private sealed interface Dialog {
    data class Options(val index: Int) : Dialog
    /** index null = nuevo. Con [insertNow] es el «texto propio»: se escribe en el chat y, si querés, se guarda. */
    data class Editor(val index: Int?, val insertNow: Boolean) : Dialog
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SnippetsScreen(
    snippets: List<String>, surface: Color, fg: Color, accent: Color,
    onAdd: (String) -> Unit, onReplace: (Int, String) -> Unit, onMoveUp: (Int) -> Unit, onRemove: (Int) -> Unit,
    actions: SnippetsActions
) {
    val muted = fg.copy(alpha = 0.59f)
    var dialog by remember { mutableStateOf<Dialog?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Mensajes guardados", color = fg, fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text("Tocá uno para escribirlo en el chat · mantené apretado para sus opciones", color = muted, fontSize = 12.sp)
            }
            Icon(
                painterResource(R.drawable.ic_close), contentDescription = "Cerrar", tint = accent,
                modifier = Modifier.size(44.dp).clickable { actions.close() }.padding(10.dp)
            )
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp)) {
            Chip("Texto propio", R.drawable.ic_edit, fg, accent) { dialog = Dialog.Editor(null, insertNow = true) }
            Chip("Agregar mensaje", R.drawable.ic_add, fg, accent) { dialog = Dialog.Editor(null, insertNow = false) }
        }
        if (snippets.isEmpty()) {
            Text(
                "Todavía no hay mensajes guardados. Tocá «Agregar mensaje» para crear uno.",
                color = muted, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
            )
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 12.dp)) {
                itemsIndexed(snippets) { i, text ->
                    Row(
                        Modifier.fillMaxWidth()
                            .combinedClickable(onClick = { actions.close(); actions.insert(text) }, onLongClick = { dialog = Dialog.Options(i) })
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(painterResource(R.drawable.ic_message), null, tint = accent, modifier = Modifier.size(24.dp))
                        Text(text, color = fg, fontSize = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        is Dialog.Options -> {
            val text = snippets.getOrNull(d.index)
            if (text == null) dialog = null else AlertDialog(
                onDismissRequest = { dialog = null },
                containerColor = surface, titleContentColor = fg, textContentColor = fg,
                title = { Text(text.take(60), fontSize = 16.sp) },
                text = {
                    Column {
                        OptionRow("Escribir en el chat", fg) { dialog = null; actions.close(); actions.insert(text) }
                        OptionRow("Editar", fg) { dialog = Dialog.Editor(d.index, insertNow = false) }
                        if (d.index > 0) OptionRow("Subir", fg) { dialog = null; onMoveUp(d.index) }
                        OptionRow("Borrar", fg) { dialog = null; onRemove(d.index); actions.message("Mensaje borrado") }
                    }
                },
                confirmButton = {}
            )
        }
        is Dialog.Editor -> {
            val initial = d.index?.let { snippets.getOrNull(it) }.orEmpty()
            var value by remember(d) { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
            val focus = remember { FocusRequester() }
            LaunchedEffect(d) { focus.requestFocus() }
            val close = { dialog = null }
            AlertDialog(
                onDismissRequest = close,
                containerColor = surface, titleContentColor = fg, textContentColor = fg,
                title = { Text(if (d.insertNow) "Texto propio" else if (d.index == null) "Nuevo mensaje guardado" else "Editar mensaje") },
                text = {
                    OutlinedTextField(
                        value = value, onValueChange = { value = it },
                        placeholder = { Text("Escribí tu mensaje") },
                        minLines = 3, maxLines = 8,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus)
                    )
                },
                confirmButton = {
                    Column(horizontalAlignment = Alignment.End) {
                        val t = value.text
                        if (d.insertNow) {
                            TextButton(enabled = t.isNotBlank(), onClick = { onAdd(t); close(); actions.close(); actions.insert(t) }) { Text("Guardar y escribir") }
                            TextButton(enabled = t.isNotBlank(), onClick = { close(); actions.close(); actions.insert(t) }) { Text("Escribir en el chat") }
                        } else {
                            TextButton(enabled = t.isNotBlank(), onClick = {
                                if (d.index == null) onAdd(t) else onReplace(d.index, t)
                                close(); actions.message("Mensaje guardado")
                            }) { Text("Guardar") }
                        }
                    }
                },
                dismissButton = { TextButton(onClick = close) { Text("Cancelar") } }
            )
        }
    }
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

@Composable
private fun OptionRow(label: String, fg: Color, onClick: () -> Unit) {
    Text(label, color = fg, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick).padding(vertical = 12.dp))
}
