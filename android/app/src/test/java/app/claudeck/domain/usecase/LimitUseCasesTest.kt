package app.claudeck.domain.usecase

import app.claudeck.domain.Clock
import app.claudeck.domain.model.Account
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

class LimitUseCasesTest {
    private val h = 3_600_000L
    private class FakeClock(var t: Long) : Clock {
        override fun now() = t
        override fun zone(): ZoneId = ZoneId.of("UTC")
    }
    private val clock = FakeClock(1_000_000L)
    private fun acc(id: String, until: Long = 0) = Account(id, "n$id", color = 1, limitUntil = until, limitStart = if (until > 0) 1 else 0)

    @Test fun `marcar limite guarda hasta y desde ahora`() {
        val r = MarkLimit(clock)(acc("a"), 1_000_000L + 5 * h)
        assertEquals(1_000_000L + 5 * h, r.limitUntil)
        assertEquals(1_000_000L, r.limitStart)
    }

    @Test fun `marcar un limite en el pasado no cambia nada`() {
        val a = acc("a")
        assertEquals(a, MarkLimit(clock)(a, 999L))
    }

    @Test fun `quitar la marca limpia hasta y desde`() {
        val r = acc("a", 5_000_000L).withoutLimit()
        assertEquals(0L, r.limitUntil)
        assertEquals(0L, r.limitStart)
    }

    @Test fun `vencer limpia solo las cuentas vencidas y las informa`() {
        val list = listOf(acc("a", 500L), acc("b", 9_999_999L), acc("c"))
        val r = ExpireLimits(clock)(list)
        assertEquals(listOf("a"), r.expired.map { it.id })
        assertEquals(0L, r.accounts[0].limitUntil)
        assertEquals(9_999_999L, r.accounts[1].limitUntil)
    }

    @Test fun `si no vence nada devuelve la misma lista`() {
        val list = listOf(acc("b", 9_999_999L))
        val r = ExpireLimits(clock)(list)
        assertEquals(list, r.accounts)
        assertEquals(emptyList<Account>(), r.expired)
    }

    @Test fun `primera disponible ignora la actual y las que descansan`() {
        val list = listOf(acc("a"), acc("b", 9_999_999L), acc("c"))
        assertEquals("c", list.firstAvailableExcept("a", clock.now())?.id)
        assertNull(listOf(acc("a"), acc("b", 9_999_999L)).firstAvailableExcept("a", clock.now()))
    }
}
