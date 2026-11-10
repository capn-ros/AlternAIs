package app.claudeck.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Color de la cuenta activa, observable por Compose. `MainActivity.retintIcons()` lo actualiza cada vez que
 * cambia la cuenta; las pantallas de Compose lo leen dentro de su composición, así que íconos, botones,
 * chips, casillas, interruptores y botones de diálogo cambian solos, sin rearmar los cajones.
 */
object AccentState {
    var color: Int by mutableIntStateOf(0xFFD2694A.toInt())
}
