package app.claudeck.domain.policy

/** Correo opcional para recordar con cuál se inicia sesión. Nunca se guardan contraseñas. */
object EmailPolicy {
    const val MAX_LENGTH = 80
    private val SHAPE = Regex("^[^\\s@]+@[^\\s@]+$")
    fun clean(raw: String?): String = raw?.trim().orEmpty().take(MAX_LENGTH).let { if (SHAPE.matches(it)) it else "" }
}
