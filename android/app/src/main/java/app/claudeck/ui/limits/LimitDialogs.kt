package app.claudeck.ui.limits

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.claudeck.domain.usecase.DayPeriod
import app.claudeck.domain.usecase.LimitOption
import app.claudeck.ui.theme.AlternTheme
import kotlinx.coroutines.delay

/** Lo que los diálogos le piden a la pantalla principal (que es quien guarda el límite). */
interface LimitActions {
    /** Marca la cuenta en descanso hasta [untilMs] (epoch ms). */
    fun apply(accountId: String, untilMs: Long)

    fun message(text: String)

    /** Colores actuales (cambian con el servicio y el tema), leídos cada vez que se dibuja un diálogo. */
    fun surface(): Int
    fun onSurface(): Int
    fun accent(): Int
}

/**
 * Vista sin tamaño propio que aloja los diálogos de Compose. Se agrega una sola vez a la Activity;
 * los diálogos viven en su propia ventana.
 */
fun createLimitHost(context: Context, vm: LimitViewModel, actions: LimitActions): ComposeView =
    ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val dialog by vm.dialog.collectAsStateWithLifecycle()
            if (dialog !is LimitDialog.None) {
                val surface = actions.surface()
                AlternTheme(dark = ColorUtils.calculateLuminance(surface) < 0.5) {
                    LimitDialogs(
                        dialog = dialog, vm = vm, actions = actions,
                        surface = Color(surface), fg = Color(actions.onSurface()), accent = Color(actions.accent())
                    )
                }
            }
        }
    }

@Composable
private fun LimitDialogs(
    dialog: LimitDialog, vm: LimitViewModel, actions: LimitActions, surface: Color, fg: Color, accent: Color
) {
    when (dialog) {
        LimitDialog.None -> Unit
        is LimitDialog.Options -> AlertDialog(
            onDismissRequest = vm::dismiss,
            containerColor = surface, titleContentColor = fg, textContentColor = fg,
            title = { Text("¿Cuándo se reinicia el límite?", fontSize = 18.sp) },
            text = {
                Column {
                    vm.options(dialog.service).forEach { option ->
                        OptionRow(option.label, fg) {
                            when (option) {
                                is LimitOption.After -> { vm.dismiss(); actions.apply(dialog.accountId, vm.untilFor(option)) }
                                is LimitOption.ExactTime -> vm.openExact(dialog.accountId)
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
        is LimitDialog.Exact -> ExactTimeDialog(
            vm = vm, surface = surface, fg = fg, accent = accent,
            onAccept = { until -> vm.dismiss(); actions.apply(dialog.accountId, until) },
            onInvalid = { actions.message("Hora no válida") }
        )
    }
}

@Composable
private fun OptionRow(text: String, fg: Color, onClick: () -> Unit) {
    Text(
        text, color = fg, fontSize = 16.sp,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp)
    )
}

/**
 * Hora exacta: horas y minutos arrancan en 0 y quedan seleccionados al tocar (se escribe sin borrar).
 * «Auto» elige solo AM o PM (lo próximo que viene); tocarlo alterna Auto → AM → PM. Con 2 cifras en la
 * hora el foco pasa a los minutos. El teclado se abre solo.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ExactTimeDialog(
    vm: LimitViewModel, surface: Color, fg: Color, accent: Color,
    onAccept: (untilMs: Long) -> Unit, onInvalid: () -> Unit
) {
    var hour by remember { mutableStateOf(TextFieldValue("0", TextRange(0, 1))) }
    var minute by remember { mutableStateOf(TextFieldValue("00", TextRange(0, 2))) }
    var period by remember { mutableStateOf(DayPeriod.AUTO) }
    val hourFocus = remember { FocusRequester() }
    val minuteFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // El teclado no siempre se abre solo con el foco: se pide explícitamente cuando el diálogo ya se mostró.
    LaunchedEffect(Unit) {
        delay(250)
        hourFocus.requestFocus()
        keyboard?.show()
    }

    val numberStyle = TextStyle(fontSize = 26.sp, textAlign = TextAlign.Center, color = fg)
    AlertDialog(
        onDismissRequest = vm::dismiss,
        containerColor = surface, titleContentColor = fg, textContentColor = fg,
        title = { Text("Hora en que se reinicia", fontSize = 18.sp) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Tocá «Auto» para elegir AM o PM a mano.", color = fg.copy(alpha = 0.7f), fontSize = 14.sp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    OutlinedTextField(
                        value = hour,
                        onValueChange = { v ->
                            val next = onlyDigits(v)
                            hour = next
                            if (next.text.length == 2) minuteFocus.requestFocus()
                        },
                        singleLine = true, textStyle = numberStyle,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        modifier = Modifier.width(84.dp).focusRequester(hourFocus)
                            .onFocusChanged { if (it.isFocused) hour = hour.copy(selection = TextRange(0, hour.text.length)) }
                    )
                    Text(":", color = fg, fontSize = 26.sp, modifier = Modifier.padding(horizontal = 6.dp))
                    OutlinedTextField(
                        value = minute,
                        onValueChange = { minute = onlyDigits(it) },
                        singleLine = true, textStyle = numberStyle,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        modifier = Modifier.width(84.dp).focusRequester(minuteFocus)
                            .onFocusChanged { if (it.isFocused) minute = minute.copy(selection = TextRange(0, minute.text.length)) }
                    )
                    Text(
                        periodLabel(period), color = accent, fontSize = 16.sp,
                        modifier = Modifier.padding(start = 16.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .border(BorderStroke(1.dp, accent), RoundedCornerShape(18.dp))
                            .clickable { period = nextPeriod(period) }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
                Text(
                    vm.previewExact(hour.text, minute.text, period),
                    color = fg.copy(alpha = 0.7f), fontSize = 14.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val target = vm.resolveExact(hour.text, minute.text, period)
                if (target == null) onInvalid() else onAccept(target.toInstant().toEpochMilli())
            }) { Text("Aceptar") }
        },
        dismissButton = { TextButton(onClick = vm::dismiss) { Text("Cancelar") } }
    )
}

private fun onlyDigits(v: TextFieldValue): TextFieldValue {
    val t = v.text.filter { it.isDigit() }.take(2)
    return if (t == v.text) v else TextFieldValue(t, TextRange(t.length))
}

private fun periodLabel(p: DayPeriod): String = when (p) {
    DayPeriod.AUTO -> "Auto"
    DayPeriod.AM -> "AM"
    DayPeriod.PM -> "PM"
}

private fun nextPeriod(p: DayPeriod): DayPeriod = when (p) {
    DayPeriod.AUTO -> DayPeriod.AM
    DayPeriod.AM -> DayPeriod.PM
    DayPeriod.PM -> DayPeriod.AUTO
}
