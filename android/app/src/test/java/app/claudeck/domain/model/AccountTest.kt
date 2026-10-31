package app.claudeck.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountTest {
    private val h = 3_600_000L
    private val now = 1_000_000_000_000L
    private fun acc(until: Long = 0, start: Long = 0) = Account("a1", "Trabajo", color = 0xFF112233.toInt(), limitUntil = until, limitStart = start)

    @Test fun `profileName usa el id`() = assertEquals("acc_a1", acc().profileName)

    @Test fun `sin limite no esta en descanso y la fraccion es 0`() {
        assertFalse(acc().isResting(now))
        assertEquals(0f, acc().limitFraction(now), 0f)
    }

    @Test fun `limite vencido deja la cuenta disponible`() {
        val a = acc(until = now - 1, start = now - h)
        assertFalse(a.isResting(now))
        assertEquals(0f, a.limitFraction(now), 0f)
    }

    @Test fun `recien marcado la fraccion es 1`() {
        assertEquals(1f, acc(until = now + 5 * h, start = now).limitFraction(now), 0.0001f)
    }

    @Test fun `a mitad del descanso la fraccion es 0,5`() {
        assertEquals(0.5f, acc(until = now + 5 * h, start = now - 5 * h).limitFraction(now), 0.0001f)
    }

    @Test fun `sin inicio guardado se supone el plazo estandar mas cercano`() {
        // faltan 2,5 h y no hay inicio: se asume 5 h -> 0,5
        assertEquals(0.5f, acc(until = now + (2.5 * h).toLong()).limitFraction(now), 0.0001f)
        // faltan 12 h: se asume 24 h -> 0,5
        assertEquals(0.5f, acc(until = now + 12 * h).limitFraction(now), 0.0001f)
    }

    @Test fun `plazo mayor que 30 dias usa lo que falta y da 1`() {
        assertEquals(1f, acc(until = now + 40 * 24 * h).limitFraction(now), 0.0001f)
    }

    @Test fun `inicio invalido posterior al final se ignora`() {
        val a = acc(until = now + 2 * h, start = now + 10 * h)
        assertTrue(a.limitFraction(now) in 0f..1f)
    }
}
