package app.claudeck.domain.policy

import org.junit.Assert.assertEquals
import org.junit.Test

class AccountInitialTest {
    @Test fun `primera letra en mayuscula`() {
        assertEquals("T", AccountInitial.of("Trabajo"))
        assertEquals("C", AccountInitial.of("  casa "))
    }

    @Test fun `salta simbolos hasta encontrar una letra o cifra`() {
        assertEquals("A", AccountInitial.of("--a"))
        assertEquals("1", AccountInitial.of("123"))
    }

    @Test fun `respeta las letras con acento y la enie`() {
        assertEquals("Ñ", AccountInitial.of("ñandú"))
        assertEquals("É", AccountInitial.of("épica"))
    }

    @Test fun `sin letras ni cifras queda un punto`() {
        assertEquals("•", AccountInitial.of(""))
        assertEquals("•", AccountInitial.of("   "))
        assertEquals("•", AccountInitial.of("!!!"))
    }
}
