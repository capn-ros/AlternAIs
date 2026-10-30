package app.claudeck.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarColorsTest {
    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()

    @Test fun `la luminancia va de 0 a 1`() {
        assertEquals(0.0, AvatarColors.luminance(black), 0.0001)
        assertEquals(1.0, AvatarColors.luminance(white), 0.0001)
    }

    @Test fun `el verde pesa mas que el rojo y este mas que el azul`() {
        val r = AvatarColors.luminance(0xFFFF0000.toInt())
        val g = AvatarColors.luminance(0xFF00FF00.toInt())
        val b = AvatarColors.luminance(0xFF0000FF.toInt())
        assertTrue(g > r && r > b)
        assertEquals(0.2126, r, 0.0001)
        assertEquals(0.7152, g, 0.0001)
        assertEquals(0.0722, b, 0.0001)
    }

    @Test fun `sobre un color claro el texto es oscuro`() {
        assertEquals(0xFF1F1F1F.toInt(), AvatarColors.textOn(0xFFFFFF00.toInt()))
        assertEquals(0xFF1F1F1F.toInt(), AvatarColors.textOn(white))
    }

    @Test fun `sobre un color oscuro o medio el texto es blanco`() {
        assertEquals(white, AvatarColors.textOn(black))
        assertEquals(white, AvatarColors.textOn(0xFFE07A5F.toInt()))
    }
}
