package app.claudeck.ui.browser

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.claudeck.R
import app.claudeck.domain.usecase.BrowserRules
import app.claudeck.ui.theme.AlternTheme

/** Lo que la barra le pide al navegador. */
interface BrowserActions {
    fun back()
    fun forward()
    /** Recarga, o detiene si todavía está cargando. */
    fun reloadOrStop()
    fun go(raw: String)
    fun close()
    fun downloadSettings()
    fun openExternal()
}

@Composable
fun BrowserBar(state: BrowserState, fg: Color, accent: Color, actions: BrowserActions) {
    val muted = fg.copy(alpha = 0.59f)
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var field by remember { mutableStateOf(TextFieldValue(state.url)) }

    // La dirección sigue a la página, salvo que se esté escribiendo.
    LaunchedEffect(state.url) {
        if (!state.urlFocused) field = TextFieldValue(state.url)
    }
    LaunchedEffect(state.clearFocusTick) {
        if (state.clearFocusTick > 0) {
            focus.clearFocus()
            keyboard?.hide()
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BarIcon(R.drawable.ic_back, "Atrás", accent, BrowserRules.buttonAlpha(state.canGoBack)) { actions.back() }
            BarIcon(R.drawable.ic_forward, "Adelante", accent, BrowserRules.buttonAlpha(state.canGoForward)) { actions.forward() }
            BarIcon(
                if (state.loading) R.drawable.ic_close else R.drawable.ic_reload,
                BrowserRules.reloadDescription(state.loading), accent, 1f
            ) { actions.reloadOrStop() }

            BasicTextField(
                value = field,
                onValueChange = { field = it },
                singleLine = true,
                textStyle = TextStyle(color = fg, fontSize = 14.sp),
                cursorBrush = SolidColor(accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    actions.go(field.text)
                }),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
                    .height(40.dp)
                    .onFocusChanged {
                        if (it.isFocused && !state.urlFocused) field = field.copy(selection = TextRange(0, field.text.length))
                        state.urlFocused = it.isFocused
                    },
                decorationBox = { inner ->
                    Box(
                        Modifier.fillMaxWidth().height(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(fg.copy(alpha = 0.08f))
                            .border(BorderStroke(1.dp, fg.copy(alpha = 0.20f)), RoundedCornerShape(20.dp))
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (field.text.isEmpty()) Text("Buscá o escribí una dirección", color = muted, fontSize = 14.sp, maxLines = 1)
                        inner()
                    }
                }
            )

            BarIcon(R.drawable.ic_close, "Cerrar el navegador", accent, 1f) { actions.close() }
        }

        if (state.progress < 100) {
            LinearProgressIndicator(progress = { state.progress / 100f }, modifier = Modifier.fillMaxWidth(), color = accent)
        } else {
            Box(Modifier.fillMaxWidth().height(4.dp))
        }

        // Accesos rápidos
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for ((label, url) in BrowserRules.QUICK_LINKS) Chip(label, fg, accent) { actions.go(url) }
            Chip("Dónde guardar", fg, accent) { actions.downloadSettings() }
            Chip("Abrir en el navegador del sistema", fg, accent) { actions.openExternal() }
        }
    }
}

@Composable
private fun BarIcon(icon: Int, desc: String, accent: Color, alpha: Float, onClick: () -> Unit) {
    Icon(
        painterResource(icon), contentDescription = desc, tint = accent,
        modifier = Modifier.size(40.dp).alpha(alpha).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 10.dp)
    )
}

@Composable
private fun Chip(label: String, fg: Color, accent: Color, onClick: () -> Unit) {
    Text(
        label, color = fg, fontSize = 13.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(accent.copy(alpha = 0.10f))
            .border(BorderStroke(1.dp, fg.copy(alpha = 0.24f)), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    )
}

/** Vista de Compose con la barra del navegador; la página (WebView) va debajo, en una vista de Android. */
fun createBrowserBar(
    context: android.content.Context, state: BrowserState, actions: BrowserActions, surface: Int, onSurface: Int, accent: Int
): androidx.compose.ui.platform.ComposeView = androidx.compose.ui.platform.ComposeView(context).apply {
    setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        AlternTheme(dark = androidx.core.graphics.ColorUtils.calculateLuminance(surface) < 0.5) {
            BrowserBar(state, Color(onSurface), Color(app.claudeck.ui.theme.AccentState.color), actions)
        }
    }
}
