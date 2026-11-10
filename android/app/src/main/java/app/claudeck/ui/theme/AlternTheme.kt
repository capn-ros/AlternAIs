package app.claudeck.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Tema de Compose. Claude va oscuro por defecto (lo decide quien la llama). El color primario es el de la cuenta activa. */
@Composable
fun AlternTheme(dark: Boolean, content: @Composable () -> Unit) {
    val accent = Color(AccentState.color)
    val onAccent = if (accent.luminance() > 0.6f) Color(0xFF1F1B18) else Color.White
    val scheme = if (dark) {
        darkColorScheme(primary = accent, onPrimary = onAccent, background = Color(0xFF191919), surface = Color(0xFF191919))
    } else {
        lightColorScheme(primary = accent, onPrimary = onAccent, background = Color(0xFFFBF8F4), surface = Color(0xFFFBF8F4))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
