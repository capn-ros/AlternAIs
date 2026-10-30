package app.claudeck.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiServiceTest {
    @Test fun `fromKey desconocido cae en Claude`() {
        assertEquals(AiService.CLAUDE, AiService.fromKey(null))
        assertEquals(AiService.CLAUDE, AiService.fromKey("otro"))
        assertEquals(AiService.REPLIT, AiService.fromKey("replit"))
    }
    @Test fun `ownsHost acepta el dominio y sus subdominios pero no parecidos`() {
        assertTrue(AiService.CLAUDE.ownsHost("claude.ai"))
        assertTrue(AiService.CLAUDE.ownsHost("api.claude.ai"))
        assertFalse(AiService.CLAUDE.ownsHost("fakeclaude.ai"))
        assertFalse(AiService.CLAUDE.ownsHost("replit.com"))
    }
}
