package app.claudeck.domain.model

/** Los 8 colores que se pueden elegir para una cuenta (ARGB opaco). Mismos valores que en Windows. */
object AccountPalette {
    val COLORS: List<Int> = listOf(
        0xFFE07A5F, 0xFFF2A541, 0xFF81B29A, 0xFF4FB0C6,
        0xFF6C8EAD, 0xFF9A7BD1, 0xFFD96C9F, 0xFF8D99AE
    ).map { it.toInt() }

    /** Color sugerido para una cuenta nueva: se van repartiendo en orden según cuántas hay. */
    fun defaultFor(existingCount: Int): Int = COLORS[existingCount.coerceAtLeast(0) % COLORS.size]
}
