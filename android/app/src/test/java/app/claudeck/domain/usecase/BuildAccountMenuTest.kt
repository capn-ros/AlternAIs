package app.claudeck.domain.usecase

import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildAccountMenuTest {
    private val zone = ZoneId.of("America/Argentina/Buenos_Aires")
    private val now = ZonedDateTime.of(2026, 10, 29, 16, 30, 0, 0, zone).toInstant().toEpochMilli()
    private val later = ZonedDateTime.of(2026, 10, 29, 21, 0, 0, 0, zone).toInstant().toEpochMilli()

    private fun menu(purpose: String = "", email: String = "", until: Long = 0L) =
        BuildAccountMenu("Trabajo", purpose, email, until, now, zone, Locale.ROOT)

    @Test fun `una cuenta disponible no ofrece quitar la marca`() {
        val actions = menu().items.map { it.action }
        assertEquals(
            listOf(
                AccountMenuAction.EDIT, AccountMenuAction.RELOAD, AccountMenuAction.MARK_LIMIT,
                AccountMenuAction.SIGN_OUT, AccountMenuAction.DELETE
            ),
            actions
        )
    }

    @Test fun `una cuenta en descanso suma quitar la marca antes de cerrar sesion`() {
        val actions = menu(until = later).items.map { it.action }
        assertEquals(6, actions.size)
        assertEquals(AccountMenuAction.CLEAR_LIMIT, actions[3])
        assertEquals(AccountMenuAction.SIGN_OUT, actions[4])
    }

    @Test fun `un descanso que ya termino cuenta como disponible`() {
        val m = menu(until = now - 1)
        assertEquals("Disponible", m.subtitle)
        assertFalse(m.items.any { it.action == AccountMenuAction.CLEAR_LIMIT })
    }

    @Test fun `el subtitulo junta proposito, correo y estado con puntos medios`() {
        assertEquals("Código · yo@correo.com · Disponible", menu("Código", "yo@correo.com").subtitle)
    }

    @Test fun `el subtitulo omite lo que esta vacio`() {
        assertEquals("Disponible", menu().subtitle)
        assertEquals("Clases · Disponible", menu(purpose = "Clases").subtitle)
    }

    @Test fun `en descanso el subtitulo dice hasta cuando`() {
        val sub = menu(until = later).subtitle
        assertTrue(sub, sub.startsWith("En descanso hasta "))
        assertTrue(sub, sub.endsWith("21:00"))
    }

    @Test fun `el titulo es el nombre de la cuenta`() = assertEquals("Trabajo", menu().title)

    @Test fun `cerrar sesion y eliminar piden confirmacion con el nombre`() {
        val out = BuildAccountMenu.confirmation(AccountMenuAction.SIGN_OUT, "Casa", "Claude")!!
        assertEquals("Cerrar sesión", out.title)
        assertTrue(out.message.contains("«Casa»"))
        val del = BuildAccountMenu.confirmation(AccountMenuAction.DELETE, "Casa", "Claude")!!
        assertEquals("Eliminar cuenta", del.title)
        assertTrue(del.message.contains("Tu cuenta de Claude no se toca."))
    }

    @Test fun `las demas acciones no piden confirmacion`() {
        listOf(AccountMenuAction.EDIT, AccountMenuAction.RELOAD, AccountMenuAction.MARK_LIMIT, AccountMenuAction.CLEAR_LIMIT)
            .forEach { assertNull(BuildAccountMenu.confirmation(it, "Casa", "Claude")) }
    }
}
