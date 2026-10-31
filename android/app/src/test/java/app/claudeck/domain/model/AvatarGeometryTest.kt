package app.claudeck.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarGeometryTest {
    // 100 de diámetro y 7 de aire arriba y abajo (la proporción de la barra: 44 dp + 7 dp).
    private val g = AvatarGeometry(100f, 114f)

    @Test fun `el centro esta en el medio del lienzo`() {
        assertEquals(50f, g.cx, 0.001f)
        assertEquals(57f, g.cy, 0.001f)
        assertEquals(7f, g.extra, 0.001f)
    }

    @Test fun `las proporciones del anillo y del disco`() {
        assertEquals(7f, g.stroke, 0.001f)
        assertEquals(46.5f, g.ringRadius, 0.001f)
        assertEquals(38f, g.diskRadius, 0.001f)
        assertEquals(36.1f, g.textSize, 0.001f)
    }

    @Test fun `el disco entra dentro del anillo y este dentro del lienzo`() {
        assertTrue(g.diskRadius < g.ringRadius - g.stroke / 2f)
        assertTrue(g.ringRadius + g.stroke / 2f <= g.width / 2f + 0.001f)
    }

    @Test fun `la rayita de cuenta activa queda en el aire de abajo`() {
        assertEquals(17f, g.markHalfWidth, 0.001f)
        assertEquals(2.94f, g.markHeight, 0.001f)
        assertEquals(110.5f, g.markCenterY, 0.001f)
        assertTrue(g.markCenterY + g.markHeight / 2f <= g.height)
        assertTrue(g.markCenterY - g.markHeight / 2f > g.cy + g.ringRadius)
    }

    @Test fun `el arco del anillo es proporcional a lo que falta`() {
        assertEquals(360f, g.sweepDegrees(1f), 0.001f)
        assertEquals(180f, g.sweepDegrees(0.5f), 0.001f)
    }

    @Test fun `el arco nunca desaparece del todo`() {
        assertEquals(7.2f, g.sweepDegrees(0f), 0.001f)
        assertEquals(7.2f, g.sweepDegrees(0.001f), 0.001f)
    }
}
