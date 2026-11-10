package app.claudeck.ui.settings

import android.content.Context
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.claudeck.R
import app.claudeck.domain.model.AiService
import app.claudeck.ui.theme.AlternTheme

/** Acciones que la pantalla le pide a quien la aloja (hoy MainActivity; después, un ViewModel de navegación). */
interface SettingsActions {
    fun toggleTheme()
    fun toggleBubble(turnOn: Boolean)
    fun reloadCurrent()
    fun openDownloads()
    fun exportAccounts()
    fun importAccounts()
    fun showAbout()
}

/** Vista de Android que aloja la pantalla de Ajustes dentro del cajón existente. */
fun createSettingsView(context: Context, vm: SettingsViewModel, actions: SettingsActions): ComposeView =
    ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val state by vm.state.collectAsStateWithLifecycle()
            AlternTheme(state.dark) { SettingsScreen(state, actions) }
        }
    }

@Composable
fun SettingsScreen(state: SettingsUiState, actions: SettingsActions) {
    val accent = Color(state.accent)
    val fg = Color(state.onSurface)
    Column(Modifier.fillMaxWidth()) {
        // Logo centrado: tocarlo alterna oscuro/claro.
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            AppLogo(Modifier.padding(horizontal = 8.dp).size(48.dp).clip(CircleShape)
                .semantics { contentDescription = "Cambiar entre tema oscuro y claro" }
                .clickable(role = Role.Button) { actions.toggleTheme() })
        }
        Text(
            "Ajustes", color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 2.dp)
        )
        SettingsRow(R.drawable.ic_bubbles, "Burbuja de cuentas", accent, fg, { actions.toggleBubble(!state.bubbleOn) }) {
            Switch(
                checked = state.bubbleOn, onCheckedChange = null,
                colors = SwitchDefaults.colors(checkedTrackColor = accent, checkedThumbColor = Color.White)
            )
        }
        SettingsRow(R.drawable.ic_reload, "Recargar cuenta actual", accent, fg, actions::reloadCurrent)
        SettingsRow(R.drawable.ic_download, "Ver descargas", accent, fg, actions::openDownloads)
        SettingsRow(R.drawable.ic_download, "Exportar cuentas", accent, fg, actions::exportAccounts)
        SettingsRow(R.drawable.ic_upload, "Importar cuentas", accent, fg, actions::importAccounts)
        SettingsRow(R.drawable.ic_info, "Acerca de AlternAIs", accent, fg, actions::showAbout)
    }
}

@Composable
private fun SettingsRow(
    @DrawableRes icon: Int, label: String, accent: Color, fg: Color, onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
        Text(label, color = fg, fontSize = 16.sp, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** El ícono de la app es adaptable (mipmap): Compose no lo dibuja con painterResource, así que va en un ImageView. */
@Composable
private fun AppLogo(modifier: Modifier) {
    AndroidView(
        factory = { ctx -> ImageView(ctx).apply { setImageResource(R.mipmap.ic_launcher) } },
        modifier = modifier
    )
}
