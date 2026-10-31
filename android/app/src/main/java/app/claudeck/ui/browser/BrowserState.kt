package app.claudeck.ui.browser

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Lo que muestra la barra del navegador. Lo escribe `BrowserPanel` (callbacks del WebView) y lo lee Compose. */
class BrowserState {
    var url by mutableStateOf("")
    var progress by mutableStateOf(100)
    var loading by mutableStateOf(false)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var urlFocused by mutableStateOf(false)
    /** Se suma para pedirle a la barra que suelte el foco (y guarde el teclado). */
    var clearFocusTick by mutableStateOf(0)
}
