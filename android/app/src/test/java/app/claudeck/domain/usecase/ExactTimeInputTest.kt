package app.claudeck.domain.usecase

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExactTimeInputTest {
    private val zone = ZoneId.of("America/Argentina/Buenos_Aires")

    /** Jueves 29/10/2026 a las 16:30. */
    private val now = ZonedDateTime.of(2026, 10, 29, 16, 30, 0, 0, zone)

    @Test fun `5 en automatico a las 16 30 es las 17 de hoy`() {
        val t = ExactTimeInput.resolve("5", "00", DayPeriod.AUTO, now)!!
        assertEquals(now.toLocalDate(), t.toLocalDate())
        assertEquals(17, t.hour)
        assertEquals(0, t.minute)
    }

    @Test fun `5 AM a las 16 30 es mañana a las 5`() {
        val t = ExactTimeInput.resolve("5", "00", DayPeriod.AM, now)!!
        assertEquals(now.toLocalDate().plusDays(1), t.toLocalDate())
        assertEquals(5, t.hour)
    }

    @Test fun `5 PM es hoy a las 17`() {
        assertEquals(17, ExactTimeInput.resolve("5", "00", DayPeriod.PM, now)!!.hour)
    }

    @Test fun `una hora mayor a 12 se toma en formato de 24 horas`() {
        val t = ExactTimeInput.resolve("17", "15", DayPeriod.AUTO, now)!!
        assertEquals(now.toLocalDate(), t.toLocalDate())
        assertEquals(17, t.hour)
        assertEquals(15, t.minute)
    }

    @Test fun `campos vacios valen cero y la medianoche es mañana`() {
        val t = ExactTimeInput.resolve("", "", DayPeriod.AUTO, now)!!
        assertEquals(now.toLocalDate().plusDays(1), t.toLocalDate())
        assertEquals(0, t.hour)
        assertEquals(0, t.minute)
    }

    @Test fun `horas o minutos fuera de rango o con letras no son validos`() {
        assertNull(ExactTimeInput.resolve("24", "00", DayPeriod.AUTO, now))
        assertNull(ExactTimeInput.resolve("5", "60", DayPeriod.AUTO, now))
        assertNull(ExactTimeInput.resolve("ab", "00", DayPeriod.AUTO, now))
    }

    @Test fun `la vista previa dice hoy o mañana y la hora con dos cifras`() {
        val today = ExactTimeInput.resolve("5", "05", DayPeriod.PM, now)
        assertEquals("Se reinicia hoy a las 17:05", ExactTimeInput.describe(today, now))
        val tomorrow = ExactTimeInput.resolve("5", "05", DayPeriod.AM, now)
        assertEquals("Se reinicia mañana a las 05:05", ExactTimeInput.describe(tomorrow, now))
    }

    @Test fun `la vista previa de una hora invalida avisa`() {
        assertEquals("Hora no válida", ExactTimeInput.describe(null, now))
    }
}
