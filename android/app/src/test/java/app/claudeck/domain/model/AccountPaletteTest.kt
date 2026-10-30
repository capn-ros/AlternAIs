package app.claudeck.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AccountPaletteTest {
    @Test fun `hay ocho colores distintos y opacos`() {
        assertEquals(8, AccountPalette.COLORS.size)
        assertEquals(8, AccountPalette.COLORS.toSet().size)
        AccountPalette.COLORS.forEach { assertEquals(0xFF, (it ushr 24) and 0xFF) }
    }

    @Test fun `el primero es el coral de siempre`() =
        assertEquals(0xFFE07A5F.toInt(), AccountPalette.COLORS.first())

    @Test fun `el color sugerido reparte en orden y da la vuelta`() {
        assertEquals(AccountPalette.COLORS[0], AccountPalette.defaultFor(0))
        assertEquals(AccountPalette.COLORS[3], AccountPalette.defaultFor(3))
        assertEquals(AccountPalette.COLORS[0], AccountPalette.defaultFor(8))
        assertEquals(AccountPalette.COLORS[1], AccountPalette.defaultFor(9))
    }

    @Test fun `el degrade del boton mas cierra el circulo volviendo al primer color`() {
        val sweep = AccountPalette.sweepColors()
        assertEquals(9, sweep.size)
        assertEquals(AccountPalette.COLORS, sweep.dropLast(1))
        assertEquals(sweep.first(), sweep.last())
    }

    @Test fun `una cantidad negativa no rompe`() =
        assertEquals(AccountPalette.COLORS[0], AccountPalette.defaultFor(-3))
}
