package app.claudeck.ui.theme

import app.claudeck.domain.model.AiService.CLAUDE
import app.claudeck.domain.model.AiService.REPLIT
import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceColorsTest {
    @Test fun `fondos medidos de las webs`() {
        assertEquals(0xFF191919.toInt(), ServiceColors.surface(CLAUDE, true))
        assertEquals(0xFFFFFFFF.toInt(), ServiceColors.surface(CLAUDE, false))
        assertEquals(0xFF1E1E1F.toInt(), ServiceColors.surface(REPLIT, true))
        assertEquals(0xFFFBF8F4.toInt(), ServiceColors.surface(REPLIT, false))
    }

    @Test fun `texto claro sobre fondo oscuro y oscuro sobre fondo claro`() {
        assertEquals(0xFFF0F0F0.toInt(), ServiceColors.onSurface(CLAUDE, true))
        assertEquals(0xFFF0F0F0.toInt(), ServiceColors.onSurface(REPLIT, true))
        assertEquals(0xFF1A1A1A.toInt(), ServiceColors.onSurface(CLAUDE, false))
        assertEquals(0xFF1F1B18.toInt(), ServiceColors.onSurface(REPLIT, false))
    }
}
