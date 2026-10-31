package app.claudeck.ui.theme

import app.claudeck.domain.model.AiService

/**
 * Fondo y texto de la interfaz según servicio y tema. Colores medidos de las webs:
 * Claude claro #FFFFFF / oscuro #191919; Replit claro crema #FBF8F4 / oscuro #1E1E1F.
 */
object ServiceColors {
    fun surface(service: AiService, dark: Boolean): Int = when (service) {
        AiService.CLAUDE -> if (dark) 0xFF191919.toInt() else 0xFFFFFFFF.toInt()
        AiService.REPLIT -> if (dark) 0xFF1E1E1F.toInt() else 0xFFFBF8F4.toInt()
    }

    fun onSurface(service: AiService, dark: Boolean): Int = when {
        dark -> 0xFFF0F0F0.toInt()
        service == AiService.CLAUDE -> 0xFF1A1A1A.toInt()
        else -> 0xFF1F1B18.toInt()
    }
}
