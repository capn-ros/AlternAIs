package app.claudeck.domain.model

/** Ajustes globales que ve la pantalla de Ajustes. [dark] es el tema del servicio activo. */
data class AppSettings(val service: AiService, val dark: Boolean, val bubbleEnabled: Boolean)
