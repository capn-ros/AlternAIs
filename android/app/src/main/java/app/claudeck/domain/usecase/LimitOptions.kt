package app.claudeck.domain.usecase

import app.claudeck.domain.model.AiService

/** Una de las maneras de indicar cuándo se reinicia el límite de uso. */
sealed interface LimitOption {
    val label: String

    /** Dentro de [hours] horas a partir de ahora. */
    data class After(override val label: String, val hours: Long) : LimitOption {
        fun untilMs(now: Long): Long = now + hours * HOUR_MS
    }

    /** Una hora del día escrita a mano. */
    data class ExactTime(override val label: String) : LimitOption
}

private const val HOUR_MS = 3_600_000L

/** Opciones según la cuota de cada IA. Claude: ventana de 5 h y tope semanal. */
object LimitOptions {
    fun forService(service: AiService): List<LimitOption> = when (service) {
        AiService.CLAUDE -> listOf(
            LimitOption.After("Dentro de 5 horas", 5),
            LimitOption.After("Dentro de 24 horas", 24),
            LimitOption.After("Dentro de 7 días", 7 * 24),
            LimitOption.ExactTime("A una hora exacta…")
        )
    }
}
