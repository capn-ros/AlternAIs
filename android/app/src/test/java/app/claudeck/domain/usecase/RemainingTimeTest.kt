package app.claudeck.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

class RemainingTimeTest {
    private val min = 60_000L
    private val hour = 60 * min
    private val day = 24 * hour

    @Test fun `menos de un minuto cuenta como 1 min`() {
        assertEquals("1 min", RemainingTime.format(0))
        assertEquals("1 min", RemainingTime.format(30_000))
        assertEquals("1 min", RemainingTime.format(-5 * min))
    }

    @Test fun `solo minutos`() {
        assertEquals("12 min", RemainingTime.format(12 * min))
        assertEquals("59 min", RemainingTime.format(59 * min + 59_000))
    }

    @Test fun `horas y minutos`() {
        assertEquals("2 h 10 min", RemainingTime.format(2 * hour + 10 * min))
        assertEquals("1 h 0 min", RemainingTime.format(hour))
        assertEquals("23 h 59 min", RemainingTime.format(day - min))
    }

    @Test fun `dias y horas, sin minutos`() {
        assertEquals("1 d 0 h", RemainingTime.format(day))
        assertEquals("3 d 4 h", RemainingTime.format(3 * day + 4 * hour + 20 * min))
        assertEquals("7 d 0 h", RemainingTime.format(7 * day))
    }
}
