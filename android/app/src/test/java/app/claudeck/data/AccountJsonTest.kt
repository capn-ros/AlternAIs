package app.claudeck.data

import app.claudeck.domain.model.Account
import app.claudeck.domain.model.AiService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountJsonTest {
    @Test fun `ida y vuelta conserva todos los campos`() {
        val a = Account("abc123", "Trabajo", "Código", 0xFFE07A5F.toInt(), 9_000L, 1_000L,
            "https://claude.ai/chat/1", "yo@mail.com", "Seguí")
        assertEquals(listOf(a), AccountJson.decode(AiService.CLAUDE, AccountJson.encode(AiService.CLAUDE, listOf(a))))
    }

    @Test fun `lee el formato viejo sin campos nuevos`() {
        val old = """[{"id":"x1","name":"Vieja","color":-1}]"""
        val a = AccountJson.decode(AiService.CLAUDE, old).single()
        assertEquals("", a.purpose); assertEquals(0L, a.limitUntil); assertEquals("", a.autoReply)
    }

    @Test fun `una lastUrl externa se descarta al leer y al escribir`() {
        val raw = """[{"id":"x","name":"n","color":1,"lastUrl":"https://evil.com/"}]"""
        assertEquals("", AccountJson.decode(AiService.CLAUDE, raw).single().lastUrl)
        val enc = AccountJson.encode(AiService.CLAUDE, listOf(Account("x", "n", color = 1, lastUrl = "https://evil.com/")))
        assertTrue(enc.contains("\"lastUrl\":\"\""))
    }

    @Test fun `json roto o vacio da lista vacia`() {
        assertEquals(emptyList<Account>(), AccountJson.decode(AiService.CLAUDE, "{no es json"))
        assertEquals(emptyList<Account>(), AccountJson.decode(AiService.CLAUDE, null))
        assertEquals(emptyList<Account>(), AccountJson.decode(AiService.CLAUDE, ""))
    }
}
