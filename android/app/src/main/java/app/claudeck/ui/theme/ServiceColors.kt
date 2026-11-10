package app.claudeck.ui.theme

import app.claudeck.domain.model.AiService

/**
 * Fondo y texto de la interfaz según servicio y tema. Colores medidos de las webs:
 * Claude claro #FFFFFF / oscuro #191919.
 */
object ServiceColors {
    fun surface(service: AiService, dark: Boolean): Int = when (service) {
        AiService.CLAUDE -> if (dark) 0xFF191919.toInt() else 0xFFFFFFFF.toInt()
    }

    fun onSurface(service: AiService, dark: Boolean): Int =
        if (dark) 0xFFF0F0F0.toInt() else 0xFF1A1A1A.toInt()
}
