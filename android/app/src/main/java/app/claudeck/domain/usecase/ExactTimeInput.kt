package app.claudeck.domain.usecase

import java.time.ZonedDateTime
import java.util.Locale

/** Texto escrito en el diálogo de «hora exacta»: lo convierte en un momento y arma la vista previa. */
object ExactTimeInput {
    /** Campos vacíos valen 0. Devuelve null si no son números o están fuera de rango. */
    fun resolve(hourText: String, minuteText: String, period: DayPeriod, now: ZonedDateTime): ZonedDateTime? {
        val hour = hourText.ifEmpty { "0" }.toIntOrNull() ?: return null
        val minute = minuteText.ifEmpty { "0" }.toIntOrNull() ?: return null
        return ResolveExactTime(hour, minute, period, now)
    }

    /** «Se reinicia hoy a las 17:00» / «Se reinicia mañana a las 05:00» / «Hora no válida». */
    fun describe(target: ZonedDateTime?, now: ZonedDateTime): String {
        if (target == null) return "Hora no válida"
        val day = if (target.toLocalDate() == now.toLocalDate()) "hoy" else "mañana"
        return "Se reinicia $day a las " + String.format(Locale.ROOT, "%02d:%02d", target.hour, target.minute)
    }
}
