package app.claudeck.ui.browser

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.claudeck.ui.common.TabUi

/** Lo que muestra la barra del navegador. Lo escribe `BrowserPanel` (callbacks del WebView) y lo lee Compose. */
class BrowserState {
    /** Pestañas abiertas y la que se está viendo. */
    var tabs by mutableStateOf<List<TabUi>>(emptyList())
    var activeTab by mutableStateOf(0)
    var url by mutableStateOf("")
    var progress by mutableStateOf(100)
    var loading by mutableStateOf(false)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var urlFocused by mutableStateOf(false)
    /** Se suma para pedirle a la barra que suelte el foco (y guarde el teclado). */
    var clearFocusTick by mutableStateOf(0)
}
