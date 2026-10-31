package app.claudeck.domain.policy

import org.junit.Assert.assertEquals
import org.junit.Test

class EmailPolicyTest {
    @Test fun `acepta un correo simple y lo recorta`() = assertEquals("a@b.com", EmailPolicy.clean("  a@b.com "))
    @Test fun `rechaza sin arroba`() = assertEquals("", EmailPolicy.clean("abc"))
    @Test fun `rechaza espacios en el medio`() = assertEquals("", EmailPolicy.clean("a b@c.com"))
    @Test fun `rechaza dos arrobas`() = assertEquals("", EmailPolicy.clean("a@b@c"))
    @Test fun `nulo da vacio`() = assertEquals("", EmailPolicy.clean(null))
    @Test fun `corta a 80 caracteres y por eso queda invalido si pierde la arroba`() =
        assertEquals("", EmailPolicy.clean("x".repeat(100) + "@a.com"))
}
