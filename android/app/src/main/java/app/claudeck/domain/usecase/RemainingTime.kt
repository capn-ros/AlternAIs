package app.claudeck.domain.usecase

/** Cuánto falta, en palabras cortas: "2 h 10 min", "3 d 4 h", "12 min" (nunca menos de 1 min). */
object RemainingTime {
    fun format(ms: Long): String {
        val totalMin = (ms / 60_000L).coerceAtLeast(1L)
        val days = totalMin / (60 * 24)
        val hours = (totalMin % (60 * 24)) / 60
        val mins = totalMin % 60
        return when {
            days > 0 -> "$days d $hours h"
            hours > 0 -> "$hours h $mins min"
            else -> "$mins min"
        }
    }
}
