package app.claudeck.domain.usecase

import java.time.ZonedDateTime

enum class DayPeriod { AUTO, AM, PM }

/**
 * «Hora exacta» del límite. Con AUTO elige AM o PM según lo próximo que viene (a las 16:30, «5» es
 * 17:00 de hoy). Horas 0 o mayores de 12 se toman como formato de 24 h. Si la hora ya pasó, es mañana.
 * Devuelve null si la hora o los minutos son inválidos.
 */
object ResolveExactTime {
    operator fun invoke(hour: Int, minute: Int, period: DayPeriod, now: ZonedDateTime): ZonedDateTime? {
        if (hour !in 0..23 || minute !in 0..59) return null
        val hours = when {
            hour == 0 || hour > 12 -> listOf(hour)
            period == DayPeriod.AM -> listOf(hour % 12)
            period == DayPeriod.PM -> listOf(hour % 12 + 12)
            else -> listOf(hour % 12, hour % 12 + 12)
        }.sorted()
        for (dayOffset in 0L..1L) {
            for (h in hours) {
                val candidate = now.toLocalDate().plusDays(dayOffset).atTime(h, minute).atZone(now.zone)
                if (candidate.isAfter(now)) return candidate
            }
        }
        return null
    }
}
