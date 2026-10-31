package app.claudeck.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class NextMonthlyRenewalTest {
    private val zone = ZoneId.of("America/Argentina/Buenos_Aires")
    private fun d(y: Int, mo: Int, day: Int, h: Int = 0) = ZonedDateTime.of(y, mo, day, h, 0, 0, 0, zone)

    @Test fun `un dia futuro de este mes`() =
        assertEquals(d(2026, 10, 31), NextMonthlyRenewal(31, d(2026, 10, 29, 12)))

    @Test fun `un dia ya pasado va al mes siguiente`() =
        assertEquals(d(2026, 11, 5), NextMonthlyRenewal(5, d(2026, 10, 29, 12)))

    @Test fun `el mismo dia pero pasada la medianoche va al mes siguiente`() =
        assertEquals(d(2026, 11, 29), NextMonthlyRenewal(29, d(2026, 10, 29, 12)))

    @Test fun `dia 31 en un mes de 30 usa el ultimo dia`() =
        assertEquals(d(2026, 11, 30), NextMonthlyRenewal(31, d(2026, 11, 2)))

    @Test fun `dia 30 en febrero usa el 28 en anio comun`() =
        assertEquals(d(2027, 2, 28), NextMonthlyRenewal(30, d(2027, 2, 1)))

    @Test fun `diciembre pasa a enero del anio siguiente`() =
        assertEquals(d(2027, 1, 10), NextMonthlyRenewal(10, d(2026, 12, 20)))

    @Test fun `dia invalido da nulo`() {
        assertNull(NextMonthlyRenewal(0, d(2026, 10, 1)))
        assertNull(NextMonthlyRenewal(32, d(2026, 10, 1)))
    }
}
