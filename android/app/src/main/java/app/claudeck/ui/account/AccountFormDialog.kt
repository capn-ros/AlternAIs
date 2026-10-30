package app.claudeck.ui.account

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.claudeck.domain.model.Account
import app.claudeck.domain.model.AccountPalette
import app.claudeck.domain.policy.EmailPolicy
import app.claudeck.domain.usecase.AccountFormResult
import app.claudeck.ui.theme.AlternTheme
import kotlinx.coroutines.delay

/** Lo que el formulario le pide a la pantalla principal (que es quien guarda las cuentas). */
interface AccountFormActions {
    /** [accountId] null = cuenta nueva; si no, se edita esa cuenta. */
    fun save(accountId: String?, data: AccountFormResult.Valid)

    /** Colores actuales (cambian con el servicio y el tema). */
    fun surface(): Int
    fun onSurface(): Int
    fun accent(): Int
}

/** Vista sin tamaño propio que aloja el formulario (el diálogo vive en su propia ventana). */
fun createAccountFormHost(context: Context, vm: AccountFormViewModel, actions: AccountFormActions): ComposeView =
    ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val form by vm.form.collectAsStateWithLifecycle()
            val current = form
            if (current != null) {
                val surface = actions.surface()
                AlternTheme(dark = ColorUtils.calculateLuminance(surface) < 0.5) {
                    AccountFormDialog(
                        initial = current, vm = vm, actions = actions,
                        surface = Color(surface), fg = Color(actions.onSurface())
                    )
                }
            }
        }
    }

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun AccountFormDialog(
    initial: AccountForm, vm: AccountFormViewModel, actions: AccountFormActions, surface: Color, fg: Color
) {
    var name by remember(initial) { mutableStateOf(initial.name) }
    var purpose by remember(initial) { mutableStateOf(initial.purpose) }
    var email by remember(initial) { mutableStateOf(initial.email) }
    var color by remember(initial) { mutableStateOf(initial.color) }
    var nameError by remember(initial) { mutableStateOf<String?>(null) }

    val nameFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(initial) {
        delay(250)
        nameFocus.requestFocus()
        keyboard?.show()
    }

    AlertDialog(
        onDismissRequest = vm::dismiss,
        containerColor = surface, titleContentColor = fg, textContentColor = fg,
        title = { Text(if (initial.accountId == null) "Nueva cuenta" else "Editar cuenta", fontSize = 20.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(Account.MAX_NAME); nameError = null },
                    label = { Text("Nombre (ej. Trabajo, Estudio)") },
                    singleLine = true,
                    isError = nameError != null,
                    supportingText = { nameError?.let { Text(it) } },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().focusRequester(nameFocus)
                )
                OutlinedTextField(
                    value = purpose,
                    onValueChange = { purpose = it.take(Account.MAX_PURPOSE) },
                    label = { Text("¿Para qué la usás? (opcional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it.take(EmailPolicy.MAX_LENGTH) },
                    label = { Text("Correo de la cuenta (opcional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
                Text("Color", color = fg.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp)) {
                    AccountPalette.COLORS.forEach { c ->
                        val selected = c == color
                        val scale by animateFloatAsState(if (selected) 1.15f else 1f, label = "colorDot")
                        Box(
                            Modifier.padding(4.dp).size(40.dp)
                                .graphicsLayer { scaleX = scale; scaleY = scale }
                                .clip(CircleShape).background(Color(c))
                                .then(if (selected) Modifier.border(3.dp, fg, CircleShape) else Modifier)
                                .clickable { color = c }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when (val result = vm.validate(name, purpose, email, color)) {
                    is AccountFormResult.Invalid -> nameError = result.nameError
                    is AccountFormResult.Valid -> { vm.dismiss(); actions.save(initial.accountId, result) }
                }
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = vm::dismiss) { Text("Cancelar") } }
    )
}
