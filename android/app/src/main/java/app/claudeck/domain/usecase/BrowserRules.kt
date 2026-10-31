package app.claudeck.domain.usecase

/** Qué hacer con lo que se escribió en la barra de direcciones. */
sealed class BrowserInput {
    /** Texto vacío: no hace nada. */
    object Ignore : BrowserInput()

    /** Cargar esta dirección. */
    data class Load(val url: String) : BrowserInput()

    /** Dirección no permitida: se avisa con [message]. */
    data class Reject(val message: String) : BrowserInput()
}

/** Qué hacer con un enlace que abre la página. */
enum class LinkAction {
    /** http/https: se carga dentro del navegador. */
    LOAD_HERE,

    /** mailto/tel: se le pasa a la app del sistema. */
    OPEN_EXTERNAL,

    /** Cualquier otro esquema (intent:, market:, etc.): se ignora. */
    BLOCK
}

/** Reglas del navegador interno que no dependen de Android. */
object BrowserRules {
    const val PROFILE = "browser"
    const val SEARCH_URL = "https://duckduckgo.com/?q="
    const val ONLY_HTTP_MESSAGE = "Solo se pueden abrir direcciones http y https"

    val QUICK_LINKS: List<Pair<String, String>> = listOf(
        "GitHub" to "https://github.com/",
        "Google" to "https://www.google.com/",
        "DuckDuckGo" to "https://duckduckgo.com/",
        "Stack Overflow" to "https://stackoverflow.com/"
    )

    /**
     * Dirección → tal cual si ya es http/https; con otro esquema se rechaza; algo como «ejemplo.com» pasa a
     * https; texto con espacios o sin punto → búsqueda en DuckDuckGo ([encode] escapa la consulta).
     */
    fun resolve(raw: String, encode: (String) -> String): BrowserInput {
        val t = raw.trim()
        if (t.isEmpty()) return BrowserInput.Ignore
        return when {
            t.startsWith("http://", true) || t.startsWith("https://", true) -> BrowserInput.Load(t)
            t.contains("://") -> BrowserInput.Reject(ONLY_HTTP_MESSAGE)
            !t.contains(' ') && t.contains('.') -> BrowserInput.Load("https://$t")
            else -> BrowserInput.Load(SEARCH_URL + encode(t))
        }
    }

    /** [scheme] es el esquema del enlace (null = relativo, que se deja pasar). */
    fun linkAction(scheme: String?): LinkAction = when (scheme?.lowercase()) {
        null, "http", "https" -> LinkAction.LOAD_HERE
        "mailto", "tel" -> LinkAction.OPEN_EXTERNAL
        else -> LinkAction.BLOCK
    }

    /** Solo se guarda como «última página» lo que es http o https. */
    fun isRememberable(url: String?): Boolean = url != null && (url.startsWith("http://") || url.startsWith("https://"))

    /** La primera vez se abre la última página guardada o, si no hay, GitHub. */
    fun startUrl(lastUrl: String?): String = lastUrl ?: QUICK_LINKS[0].second

    /** Sin «; wv» sitios como GitHub no tratan al navegador como uno incrustado. */
    fun cleanUserAgent(ua: String): String = ua.replace("; wv", "")

    /** Qué hace «Atrás»: salir del campo de dirección, volver en el historial o cerrar. */
    fun back(urlFieldFocused: Boolean, canGoBack: Boolean): BrowserBack = when {
        urlFieldFocused -> BrowserBack.LEAVE_FIELD
        canGoBack -> BrowserBack.GO_BACK
        else -> BrowserBack.CLOSE
    }

    fun reloadDescription(loading: Boolean) = if (loading) "Detener" else "Recargar"
    fun buttonAlpha(enabled: Boolean) = if (enabled) 1f else 0.3f
}

enum class BrowserBack { LEAVE_FIELD, GO_BACK, CLOSE }
