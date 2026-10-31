package app.claudeck.domain.model

/** Cuenta inmutable. Los cambios se hacen con `copy` desde los casos de uso. */
data class Account(
    val id: String,
    val name: String,
    val purpose: String = "",
    /** Color ARGB. */
    val color: Int,
    /** Epoch ms hasta el que la cuenta descansa por límite de uso. 0 = disponible. */
    val limitUntil: Long = 0L,
    /** Epoch ms en que se marcó el límite. 0 = no se sabe (marcas viejas). */
    val limitStart: Long = 0L,
    val lastUrl: String = "",
    val email: String = "",
    /** Mensaje que se envía solo al terminar el descanso ("" = ninguno). */
    val autoReply: String = ""
) {
    /** Cada cuenta usa un perfil de WebView propio. No cambiar nunca el id de una cuenta existente. */
    val profileName: String get() = "acc_$id"

    fun isResting(now: Long): Boolean = limitUntil > now

    /**
     * Qué fracción del descanso falta (1 = recién marcado, 0 = terminó). Sin inicio guardado se supone
     * el plazo estándar más cercano (5 h, 24 h, 7 d o 30 d).
     */
    fun limitFraction(now: Long): Float {
        if (!isResting(now)) return 0f
        val remaining = limitUntil - now
        val total = if (limitStart in 1 until limitUntil) {
            limitUntil - limitStart
        } else {
            STANDARD_PERIODS.firstOrNull { it >= remaining } ?: remaining
        }
        return (remaining.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }

    companion object {
        /** Id nuevo (12 caracteres). Una vez creado, nunca se cambia: de él depende el perfil de WebView. */
        fun newId(): String = java.util.UUID.randomUUID().toString().replace("-", "").take(12)

        private const val HOUR = 3_600_000L
        private val STANDARD_PERIODS = listOf(5 * HOUR, 24 * HOUR, 7 * 24 * HOUR, 30 * 24 * HOUR)
        const val MAX_NAME = 24
        const val MAX_PURPOSE = 60
    }
}
