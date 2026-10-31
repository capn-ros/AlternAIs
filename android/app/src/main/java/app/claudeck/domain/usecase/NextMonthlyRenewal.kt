package app.claudeck.domain.usecase

import java.time.YearMonth
import java.time.ZonedDateTime

/** Renovación mensual (Replit): próximo día del mes a las 00:00; si el mes es más corto, el último día. */
object NextMonthlyRenewal {
    operator fun invoke(dayOfMonth: Int, now: ZonedDateTime): ZonedDateTime? {
        if (dayOfMonth !in 1..31) return null
        fun inMonth(ym: YearMonth) =
            ym.atDay(minOf(dayOfMonth, ym.lengthOfMonth())).atStartOfDay(now.zone)
        val current = inMonth(YearMonth.from(now))
        return if (current.isAfter(now)) current else inMonth(YearMonth.from(now).plusMonths(1))
    }
}
