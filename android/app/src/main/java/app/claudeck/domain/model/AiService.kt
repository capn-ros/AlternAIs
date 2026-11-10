package app.claudeck.domain.model

/**
 * Servicio de IA que contiene la app (hoy solo Claude): sitio propio y cuentas propias.
 * Dominio puro: no conoce colores de Android ni temas (eso es de la capa de UI).
 */
enum class AiService(val key: String, val label: String, val startUrl: String, val hosts: List<String>) {
    CLAUDE("claude", "Claude", "https://claude.ai/", listOf("claude.ai"));

    fun ownsHost(host: String): Boolean = hosts.any { h -> host == h || host.endsWith(".$h") }

    companion object {
        fun fromKey(key: String?): AiService = entries.firstOrNull { it.key == key } ?: CLAUDE
    }
}
