package app.claudeck.ui.theme

import app.claudeck.domain.model.AiService.CLAUDE
import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceColorsTest {
    @Test fun `fondos medidos de las webs`() {
        assertEquals(0xFF191919.toInt(), ServiceColors.surface(CLAUDE, true))
        assertEquals(0xFFFFFFFF.toInt(), ServiceColors.surface(CLAUDE, false))
    }

    @Test fun `texto claro sobre fondo oscuro y oscuro sobre fondo claro`() {
        assertEquals(0xFFF0F0F0.toInt(), ServiceColors.onSurface(CLAUDE, true))
        assertEquals(0xFF1A1A1A.toInt(), ServiceColors.onSurface(CLAUDE, false))
    }
}
