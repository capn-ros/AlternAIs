package app.claudeck.domain.policy

import app.claudeck.domain.model.AiService
import java.net.URI

/**
 * Solo se restaura una URL HTTPS del sitio del servicio, sin query ni fragmento, sin credenciales
 * y sin puertos raros. Nunca un destino externo guardado en el estado. Usa java.net.URI para poder
 * probarse en la JVM sin Android.
 */
object UrlRestorePolicy {
    private const val MAX_LENGTH = 4096

    fun restorable(service: AiService, raw: String?): String? {
        val value = raw?.takeIf { it.isNotBlank() && it.length <= MAX_LENGTH } ?: return null
        val uri = try {
            URI(value)
        } catch (_: Exception) {
            return null
        }
        val host = uri.host?.lowercase() ?: return null
        val validPort = uri.port == -1 || uri.port == 443
        if (!"https".equals(uri.scheme, ignoreCase = true) || !service.ownsHost(host) ||
            uri.rawUserInfo != null || !validPort
        ) return null
        return "https://" + uri.rawAuthority + (uri.rawPath ?: "")
    }
}
