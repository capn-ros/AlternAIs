package app.claudeck.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Brand = Color(0xFFD2694A)

/** Tema de Compose. Claude va oscuro por defecto (lo decide quien la llama). */
@Composable
fun AlternTheme(dark: Boolean, content: @Composable () -> Unit) {
    val scheme = if (dark) {
        darkColorScheme(primary = Brand, background = Color(0xFF191919), surface = Color(0xFF191919))
    } else {
        lightColorScheme(primary = Brand, background = Color(0xFFFBF8F4), surface = Color(0xFFFBF8F4))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
