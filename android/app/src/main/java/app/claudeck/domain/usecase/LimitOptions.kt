package app.claudeck.domain.usecase

import app.claudeck.domain.model.AiService

/** Una de las maneras de indicar cuándo se reinicia el límite de uso. */
sealed interface LimitOption {
    val label: String

    /** Dentro de [hours] horas a partir de ahora. */
    data class After(override val label: String, val hours: Long) : LimitOption {
        fun untilMs(now: Long): Long = now + hours * HOUR_MS
    }

    /** Replit: día de renovación mensual de los créditos (a las 00:00). */
    data class MonthlyRenewal(override val label: String) : LimitOption

    /** Una hora del día escrita a mano. */
    data class ExactTime(override val label: String) : LimitOption
}

private const val HOUR_MS = 3_600_000L

/**
 * Opciones según la cuota de cada IA. Claude: ventana de 5 h y tope semanal. Replit: Modo gratis
 * (se renueva cada 5 h y tiene tope semanal), créditos diarios del plan Starter y créditos mensuales
 * que se renuevan al empezar cada ciclo de facturación.
 */
object LimitOptions {
    fun forService(service: AiService): List<LimitOption> = when (service) {
        AiService.CLAUDE -> listOf(
            LimitOption.After("Dentro de 5 horas", 5),
            LimitOption.After("Dentro de 24 horas", 24),
            LimitOption.After("Dentro de 7 días", 7 * 24),
            LimitOption.ExactTime("A una hora exacta…")
        )
        AiService.REPLIT -> listOf(
            LimitOption.After("Dentro de 5 horas (Modo gratis)", 5),
            LimitOption.After("Dentro de 24 horas (créditos diarios)", 24),
            LimitOption.After("Dentro de 7 días (tope semanal)", 7 * 24),
            LimitOption.MonthlyRenewal("En la renovación mensual…"),
            LimitOption.ExactTime("A una hora exacta…")
        )
    }
}
