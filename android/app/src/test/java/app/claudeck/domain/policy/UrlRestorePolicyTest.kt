package app.claudeck.domain.policy

import app.claudeck.domain.model.AiService.CLAUDE
import app.claudeck.domain.model.AiService.REPLIT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlRestorePolicyTest {
    private fun claude(u: String?) = UrlRestorePolicy.restorable(CLAUDE, u)

    @Test fun `acepta https del sitio y saca query y fragmento`() {
        assertEquals("https://claude.ai/chat/abc", claude("https://claude.ai/chat/abc?x=1#frag"))
    }

    @Test fun `acepta subdominios del servicio`() {
        assertEquals("https://www.claude.ai/new", claude("https://www.claude.ai/new"))
    }

    @Test fun `acepta el puerto 443 explicito`() {
        assertEquals("https://claude.ai:443/new", claude("https://claude.ai:443/new"))
    }

    @Test fun `rechaza http`() = assertNull(claude("http://claude.ai/chat"))
    @Test fun `rechaza otro dominio`() = assertNull(claude("https://evil.com/chat"))
    @Test fun `rechaza dominio que solo termina parecido`() = assertNull(claude("https://notclaude.ai/chat"))
    @Test fun `rechaza credenciales en la url`() = assertNull(claude("https://user:pw@claude.ai/chat"))
    @Test fun `rechaza puertos distintos de 443`() = assertNull(claude("https://claude.ai:8443/chat"))
    @Test fun `rechaza esquemas peligrosos`() {
        assertNull(claude("javascript:alert(1)"))
        assertNull(claude("file:///sdcard/x"))
    }
    @Test fun `rechaza vacio nulo y demasiado largo`() {
        assertNull(claude(null))
        assertNull(claude("   "))
        assertNull(claude("https://claude.ai/" + "a".repeat(5000)))
    }
    @Test fun `rechaza texto que no es url`() = assertNull(claude("no es una url ::"))
    @Test fun `una url de claude no sirve para replit`() {
        assertNull(UrlRestorePolicy.restorable(REPLIT, "https://claude.ai/chat"))
        assertEquals("https://replit.com/@u/p", UrlRestorePolicy.restorable(REPLIT, "https://replit.com/@u/p"))
    }
}
