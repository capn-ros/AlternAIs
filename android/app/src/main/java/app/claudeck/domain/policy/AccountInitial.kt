package app.claudeck.domain.policy

/** La letra o número que se ve dentro del círculo de la cuenta. */
object AccountInitial {
    /** Primera letra o cifra del nombre, en mayúscula; «•» si no hay ninguna. */
    fun of(name: String): String {
        val c = name.trim().firstOrNull { it.isLetterOrDigit() } ?: return "•"
        return c.uppercaseChar().toString()
    }
}
