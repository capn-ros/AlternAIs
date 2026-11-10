package app.claudeck.domain.usecase

import app.claudeck.domain.model.AiService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LimitOptionsTest {
    @Test fun `Claude ofrece cuatro opciones con la hora exacta al final`() {
        val o = LimitOptions.forService(AiService.CLAUDE)
        assertEquals(
            listOf("Dentro de 5 horas", "Dentro de 24 horas", "Dentro de 7 días", "A una hora exacta…"),
            o.map { it.label }
        )
        assertTrue(o.last() is LimitOption.ExactTime)
    }

    @Test fun `las opciones de horas suman 5, 24 y 168 horas`() {
        val hours = LimitOptions.forService(AiService.CLAUDE).filterIsInstance<LimitOption.After>().map { it.hours }
        assertEquals(listOf(5L, 24L, 168L), hours)
    }

    @Test fun `dentro de N horas se calcula desde ahora`() {
        val after = LimitOption.After("x", 5)
        assertEquals(1_000L + 5 * 3_600_000L, after.untilMs(1_000L))
    }
}
