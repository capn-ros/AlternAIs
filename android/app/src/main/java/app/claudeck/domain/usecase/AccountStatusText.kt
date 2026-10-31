package app.claudeck.domain.usecase

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Los textos de estado de una cuenta (barra de abajo, círculos y accesibilidad). [limitUntil] 0 = disponible. */
object AccountStatusText {
    private const val SEPARATOR = " · "

    private fun resting(limitUntil: Long, now: Long) = limitUntil > now

    /** Descripción de accesibilidad del círculo: «Trabajo» o «Trabajo, en descanso, faltan 2 h 10 min». */
    fun circleDescription(name: String, limitUntil: Long, now: Long): String =
        if (resting(limitUntil, now)) "$name, en descanso, faltan " + RemainingTime.format(limitUntil - now) else name

    /** Barra de tiempo: «Trabajo · faltan 2 h 10 min» o «Trabajo · Disponible». */
    fun timeBarText(name: String, limitUntil: Long, now: Long): String =
        if (resting(limitUntil, now)) "$name · faltan " + RemainingTime.format(limitUntil - now) else "$name · Disponible"

    /** Línea de la barra desplegada: nombre · propósito (si hay) · estado. */
    fun infoLine(name: String, purpose: String, limitUntil: Long, now: Long, zone: ZoneId, locale: Locale): String {
        val parts = mutableListOf(name)
        if (purpose.isNotBlank()) parts.add(purpose)
        if (resting(limitUntil, now)) {
            val until = DateTimeFormatter.ofPattern("EEE HH:mm", locale).format(Instant.ofEpochMilli(limitUntil).atZone(zone))
            parts.add("En descanso hasta $until (faltan ${RemainingTime.format(limitUntil - now)})")
        } else {
            parts.add("Disponible")
        }
        return parts.joinToString(SEPARATOR)
    }
}
