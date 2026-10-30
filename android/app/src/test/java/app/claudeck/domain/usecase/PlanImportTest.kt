package app.claudeck.domain.usecase

import app.claudeck.domain.model.Account
import app.claudeck.domain.model.AiService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlanImportTest {
    private var n = 0
    private val palette = listOf(0xFF000001.toInt(), 0xFF000002.toInt())
    private val plan = PlanImport({ "id${++n}" }, palette)
    private val now = 1_000L
    private fun run(
        items: List<ImportedAccount>,
        existing: List<Account> = emptyList(),
        hasSelected: Boolean = false
    ) = plan(AiService.CLAUDE, existing, items, hasSelected, now)

    @Test fun `agrega cuentas nuevas con id propio`() {
        val r = run(listOf(ImportedAccount("Trabajo", colorHex = "#E07A5F")))
        assertEquals(1, r.added)
        assertEquals("id1", r.accounts.single().id)
        assertEquals(0xFFE07A5F.toInt(), r.accounts.single().color)
    }

    @Test fun `omite nombres repetidos sin importar mayusculas y vacios`() {
        val ex = listOf(Account("x", "Trabajo", color = 1))
        val r = run(listOf(ImportedAccount("trabajo"), ImportedAccount("   "), ImportedAccount("Casa")), ex)
        assertEquals(1, r.added)
        assertEquals(2, r.skipped)
    }

    @Test fun `repetidos dentro del mismo archivo tambien se omiten`() {
        val r = run(listOf(ImportedAccount("A"), ImportedAccount("a")))
        assertEquals(1, r.added)
        assertEquals(1, r.skipped)
    }

    @Test fun `color invalido usa la paleta segun la posicion`() {
        val r = run(listOf(ImportedAccount("A", colorHex = "rojo"), ImportedAccount("B", colorHex = "")))
        assertEquals(palette[0], r.accounts[0].color)
        assertEquals(palette[1], r.accounts[1].color)
    }

    @Test fun `color ARGB de 8 digitos se respeta`() =
        assertEquals(0x80112233.toInt(), PlanImport.parseColor("#80112233"))

    @Test fun `solo conserva limites todavia vigentes`() {
        val r = run(listOf(
            ImportedAccount("Vigente", limitUntil = 5_000L, limitStart = 100L),
            ImportedAccount("Vencido", limitUntil = 500L, limitStart = 100L)
        ))
        assertEquals(5_000L, r.accounts[0].limitUntil)
        assertEquals(0L, r.accounts[1].limitUntil)
        assertEquals(0L, r.accounts[1].limitStart)
    }

    @Test fun `corta nombre a 24 y proposito a 60`() {
        val r = run(listOf(ImportedAccount("n".repeat(40), purpose = "p".repeat(100))))
        assertEquals(24, r.accounts[0].name.length)
        assertEquals(60, r.accounts[0].purpose.length)
    }

    @Test fun `maximo 50 por servicio`() {
        val many = (1..80).map { ImportedAccount("c$it") }
        assertEquals(50, run(many).added)
    }

    @Test fun `sanea url y correo`() {
        val r = run(listOf(ImportedAccount("A", lastUrl = "https://evil.com/x", email = "mal formado")))
        assertEquals("", r.accounts[0].lastUrl)
        assertEquals("", r.accounts[0].email)
    }

    @Test fun `sin cuenta activa y lista vacia queda activa la primera importada`() {
        val r = run(listOf(ImportedAccount("A"), ImportedAccount("B", selected = true)))
        assertEquals("id1", r.newSelectedId)
    }

    @Test fun `sin cuenta activa pero con cuentas previas queda activa la marcada en el archivo`() {
        val ex = listOf(Account("x", "Previa", color = 1))
        val r = run(listOf(ImportedAccount("A"), ImportedAccount("B", selected = true)), ex)
        assertEquals("id2", r.newSelectedId)
    }

    @Test fun `si ya habia cuenta activa no se cambia`() {
        val r = run(listOf(ImportedAccount("A", selected = true)), hasSelected = true)
        assertNull(r.newSelectedId)
    }
}
