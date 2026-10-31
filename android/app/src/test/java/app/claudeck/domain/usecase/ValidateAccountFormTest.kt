package app.claudeck.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

class ValidateAccountFormTest {
    private val color = 0xFF112233.toInt()

    private fun valid(name: String = "Trabajo", purpose: String = "", email: String = "") =
        ValidateAccountForm(name, purpose, email, color) as AccountFormResult.Valid

    @Test fun `un nombre vacio pide poner un nombre`() =
        assertEquals(AccountFormResult.Invalid("Poné un nombre"), ValidateAccountForm("", "", "", color))

    @Test fun `un nombre de solo espacios tambien`() =
        assertEquals(AccountFormResult.Invalid("Poné un nombre"), ValidateAccountForm("   ", "x", "a@b.com", color))

    @Test fun `el nombre se recorta`() = assertEquals("Estudio", valid(name = "  Estudio ").name)

    @Test fun `el nombre se corta a 24 caracteres`() =
        assertEquals("a".repeat(24), valid(name = "a".repeat(40)).name)

    @Test fun `el proposito es opcional, se recorta y se corta a 60`() {
        assertEquals("", valid(purpose = "   ").purpose)
        assertEquals("Clases", valid(purpose = " Clases ").purpose)
        assertEquals("b".repeat(60), valid(purpose = "b".repeat(100)).purpose)
    }

    @Test fun `un correo valido se conserva y uno invalido queda vacio`() {
        assertEquals("yo@correo.com", valid(email = " yo@correo.com ").email)
        assertEquals("", valid(email = "no es un correo").email)
    }

    @Test fun `el color pasa tal cual`() = assertEquals(color, valid().color)
}
