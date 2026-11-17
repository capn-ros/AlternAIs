package app.claudeck.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiServiceTest {
    @Test fun `fromKey desconocido cae en Claude`() {
        assertEquals(AiService.CLAUDE, AiService.fromKey(null))
        assertEquals(AiService.CLAUDE, AiService.fromKey("otro"))
    }
    @Test fun `fromKey reconoce cada IA`() {
        assertEquals(AiService.REPLIT, AiService.fromKey("replit"))
        assertEquals(AiService.CHATGPT, AiService.fromKey("chatgpt"))
        assertEquals(AiService.GEMINI, AiService.fromKey("gemini"))
        assertEquals(AiService.GROK, AiService.fromKey("grok"))
        assertEquals(AiService.DEEPSEEK, AiService.fromKey("deepseek"))
    }
    @Test fun `Claude es la principal y va primero`() {
        assertEquals(AiService.CLAUDE, AiService.entries.first())
        assertTrue(AiService.CLAUDE.isMain)
        assertFalse(AiService.GEMINI.isMain)
    }
    @Test fun `las claves y los sitios no se repiten`() {
        assertEquals(AiService.entries.size, AiService.entries.map { it.key }.toSet().size)
        val hosts = AiService.entries.flatMap { it.hosts }
        assertEquals(hosts.size, hosts.toSet().size) // ningún sitio pertenece a dos IAs
    }
    @Test fun `Replit arranca claro y las demas oscuras`() {
        assertFalse(AiService.REPLIT.defaultDark)
        assertTrue(AiService.CLAUDE.defaultDark)
        assertTrue(AiService.CHATGPT.defaultDark)
    }
    @Test fun `ownsHost acepta el dominio y sus subdominios pero no parecidos`() {
        assertTrue(AiService.CLAUDE.ownsHost("claude.ai"))
        assertTrue(AiService.CLAUDE.ownsHost("api.claude.ai"))
        assertFalse(AiService.CLAUDE.ownsHost("fakeclaude.ai"))
        assertFalse(AiService.CLAUDE.ownsHost("replit.com"))
        assertTrue(AiService.REPLIT.ownsHost("replit.com"))
        assertTrue(AiService.GEMINI.ownsHost("gemini.google.com"))
        assertFalse(AiService.GEMINI.ownsHost("mail.google.com"))
    }
}
