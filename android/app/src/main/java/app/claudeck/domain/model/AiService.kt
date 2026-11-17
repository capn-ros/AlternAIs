package app.claudeck.domain.model

/**
 * Servicio de IA que contiene la app: Claude es la principal (la que abre por defecto); las demás se eligen
 * desde Ajustes. Cada una tiene su sitio y sus cuentas propias. Dominio puro: no conoce colores de Android.
 */
enum class AiService(
    val key: String,
    val label: String,
    val startUrl: String,
    val hosts: List<String>,
    /** Tema con el que arranca (el usuario lo puede alternar y se recuerda por servicio). */
    val defaultDark: Boolean = true
) {
    CLAUDE("claude", "Claude", "https://claude.ai/", listOf("claude.ai")),
    CHATGPT("chatgpt", "ChatGPT", "https://chatgpt.com/", listOf("chatgpt.com", "openai.com")),
    GEMINI("gemini", "Gemini", "https://gemini.google.com/", listOf("gemini.google.com")),
    GROK("grok", "Grok", "https://grok.com/", listOf("grok.com", "x.ai")),
    DEEPSEEK("deepseek", "DeepSeek", "https://chat.deepseek.com/", listOf("deepseek.com")),
    REPLIT("replit", "Replit", "https://replit.com/", listOf("replit.com", "replit.dev", "repl.co"), defaultDark = false);

    fun ownsHost(host: String): Boolean = hosts.any { h -> host == h || host.endsWith(".$h") }

    /** Claude es la principal: no se puede quitar y es lo que se abre si falta o se corrompe la preferencia. */
    val isMain: Boolean get() = this == CLAUDE

    companion object {
        fun fromKey(key: String?): AiService = entries.firstOrNull { it.key == key } ?: CLAUDE
    }
}
