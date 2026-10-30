package app.claudeck.domain.model

/**
 * Medidas de la barra de tiempo de la barra plegada (pastilla con texto: el relleno es lo que falta de
 * descanso). Sin nada de Android: el dibujo mide el texto y le pregunta a esto qué hacer.
 */
object TimeBarLayout {
    /** Fracción de relleno siempre entre 0 y 1. */
    fun clampFraction(fraction: Float): Float = fraction.coerceIn(0f, 1f)

    /** Pastilla: las puntas son semicírculos. */
    fun cornerRadius(height: Float): Float = height / 2f

    fun textSize(height: Float): Float = height * 0.5f

    /** Ancho disponible para el texto: se deja media altura de aire a cada lado. */
    fun textAvailableWidth(width: Float, height: Float): Float = width - height

    /** Ancho del relleno. */
    fun fillWidth(width: Float, fraction: Float): Float = width * clampFraction(fraction)

    /** Línea base del texto para que quede centrado en vertical. */
    fun baseline(height: Float, ascent: Float, descent: Float): Float = height / 2f - (descent + ascent) / 2f

    /** Si caben solo [fits] caracteres, corta ahí y agrega «…»; si caben todos, deja el texto igual. */
    fun ellipsize(label: String, fits: Int): String =
        if (fits < label.length) label.substring(0, fits.coerceAtLeast(0)).trimEnd() + "…" else label
}
