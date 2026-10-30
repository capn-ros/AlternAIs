package app.claudeck.domain.usecase

import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountStatusTextTest {
    private val zone = ZoneId.of("America/Argentina/Buenos_Aires")
    private val now = ZonedDateTime.of(2026, 10, 29, 16, 30, 0, 0, zone).toInstant().toEpochMilli()
    private val in2h10 = now + 2 * 3_600_000L + 10 * 60_000L

    @Test fun `el circulo de una cuenta disponible solo dice el nombre`() =
        assertEquals("Trabajo", AccountStatusText.circleDescription("Trabajo", 0L, now))

    @Test fun `el circulo de una cuenta en descanso dice cuanto falta`() =
        assertEquals("Trabajo, en descanso, faltan 2 h 10 min", AccountStatusText.circleDescription("Trabajo", in2h10, now))

    @Test fun `un descanso vencido cuenta como disponible`() {
        assertEquals("Trabajo", AccountStatusText.circleDescription("Trabajo", now - 1, now))
        assertEquals("Trabajo · Disponible", AccountStatusText.timeBarText("Trabajo", now - 1, now))
    }

    @Test fun `la barra de tiempo`() {
        assertEquals("Trabajo · Disponible", AccountStatusText.timeBarText("Trabajo", 0L, now))
        assertEquals("Trabajo · faltan 2 h 10 min", AccountStatusText.timeBarText("Trabajo", in2h10, now))
    }

    @Test fun `la linea desplegada de una cuenta disponible`() {
        assertEquals("Trabajo · Disponible", AccountStatusText.infoLine("Trabajo", "  ", 0L, now, zone, Locale.ROOT))
        assertEquals("Trabajo · Código · Disponible", AccountStatusText.infoLine("Trabajo", "Código", 0L, now, zone, Locale.ROOT))
    }

    @Test fun `la linea desplegada de una cuenta en descanso`() {
        val line = AccountStatusText.infoLine("Trabajo", "Código", in2h10, now, zone, Locale.ROOT)
        assertTrue(line, line.startsWith("Trabajo · Código · En descanso hasta "))
        assertTrue(line, line.contains("18:40 (faltan 2 h 10 min)"))
    }
}
