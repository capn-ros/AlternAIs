package app.claudeck.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ResolveExactTimeTest {
    private val zone = ZoneId.of("America/Argentina/Buenos_Aires")
    private fun at(h: Int, m: Int) = ZonedDateTime.of(2026, 10, 29, h, m, 0, 0, zone)
    private fun resolve(h: Int, m: Int, p: DayPeriod, now: ZonedDateTime) = ResolveExactTime(h, m, p, now)

    @Test fun `auto a las 16 30 el 5 es 17 00 de hoy`() =
        assertEquals(at(17, 0), resolve(5, 0, DayPeriod.AUTO, at(16, 30)))

    @Test fun `auto a las 8 30 el 5 es 5 PM de hoy porque las 5 AM ya pasaron`() =
        assertEquals(at(17, 0), resolve(5, 0, DayPeriod.AUTO, at(8, 30)))

    @Test fun `auto a las 3 30 el 5 es 5 AM de hoy`() =
        assertEquals(at(5, 0), resolve(5, 0, DayPeriod.AUTO, at(3, 30)))

    @Test fun `auto despues de las 17 el 5 pasa a 5 AM de manana`() {
        val r = resolve(5, 0, DayPeriod.AUTO, at(18, 0))!!
        assertEquals(at(5, 0).plusDays(1), r)
    }

    @Test fun `AM forzado ya pasado va a manana`() =
        assertEquals(at(5, 0).plusDays(1), resolve(5, 0, DayPeriod.AM, at(9, 0)))

    @Test fun `PM forzado`() = assertEquals(at(21, 15), resolve(9, 15, DayPeriod.PM, at(9, 0)))

    @Test fun `formato 24 horas ignora AM o PM`() {
        assertEquals(at(17, 0), resolve(17, 0, DayPeriod.AM, at(9, 0)))
        assertEquals(at(0, 0).plusDays(1), resolve(0, 0, DayPeriod.PM, at(9, 0)))
    }

    @Test fun `12 en auto puede ser medianoche o mediodia`() {
        assertEquals(at(12, 0), resolve(12, 0, DayPeriod.AUTO, at(9, 0)))
        assertEquals(at(12, 0).plusDays(1).withHour(0), resolve(12, 0, DayPeriod.AUTO, at(13, 0)))
    }

    @Test fun `la hora exacta actual cuenta como ya pasada`() =
        assertEquals(at(17, 0).plusDays(1), resolve(17, 0, DayPeriod.AUTO, at(17, 0)))

    @Test fun `valores invalidos devuelven nulo`() {
        assertNull(resolve(24, 0, DayPeriod.AUTO, at(9, 0)))
        assertNull(resolve(-1, 0, DayPeriod.AUTO, at(9, 0)))
        assertNull(resolve(5, 60, DayPeriod.AUTO, at(9, 0)))
    }
}
