package app.claudeck.domain.policy

/** Correo opcional para recordar con cuál se inicia sesión. Nunca se guardan contraseñas. */
object EmailPolicy {
    private val SHAPE = Regex("^[^\\s@]+@[^\\s@]+$")
    fun clean(raw: String?): String = raw?.trim().orEmpty().take(80).let { if (SHAPE.matches(it)) it else "" }
}
