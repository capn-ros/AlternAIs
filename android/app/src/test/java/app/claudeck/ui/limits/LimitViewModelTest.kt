package app.claudeck.ui.limits

import app.claudeck.domain.Clock
import app.claudeck.domain.model.AiService
import app.claudeck.domain.usecase.DayPeriod
import app.claudeck.domain.usecase.LimitOption
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LimitViewModelTest {
    private val zone = ZoneId.of("America/Argentina/Buenos_Aires")
    private val start = ZonedDateTime.of(2026, 10, 29, 16, 30, 0, 0, zone).toInstant().toEpochMilli()

    private val clock = object : Clock {
        override fun now(): Long = start
        override fun zone(): ZoneId = zone
    }

    @Test fun `arranca sin ningun dialogo`() {
        assertEquals(LimitDialog.None, LimitViewModel(clock).dialog.value)
    }

    @Test fun `abrir las opciones guarda la cuenta y el servicio`() {
        val vm = LimitViewModel(clock)
        vm.openOptions("abc", AiService.REPLIT)
        assertEquals(LimitDialog.Options("abc", AiService.REPLIT), vm.dialog.value)
    }

    @Test fun `pasar a la hora exacta conserva la cuenta y cerrar vuelve a ninguno`() {
        val vm = LimitViewModel(clock)
        vm.openOptions("abc", AiService.CLAUDE)
        vm.openExact("abc")
        assertEquals(LimitDialog.Exact("abc"), vm.dialog.value)
        vm.dismiss()
        assertEquals(LimitDialog.None, vm.dialog.value)
    }

    @Test fun `dentro de 5 horas suma 5 horas al reloj`() {
        val vm = LimitViewModel(clock)
        assertEquals(start + 5 * 3_600_000L, vm.untilFor(LimitOption.After("x", 5)))
    }

    @Test fun `las opciones dependen del servicio`() {
        val vm = LimitViewModel(clock)
        assertEquals(4, vm.options(AiService.CLAUDE).size)
        assertEquals(5, vm.options(AiService.REPLIT).size)
    }

    @Test fun `la hora exacta usa el reloj inyectado`() {
        val vm = LimitViewModel(clock)
        val t = vm.resolveExact("5", "00", DayPeriod.AUTO)!!
        assertEquals(17, t.hour)
        assertEquals("Se reinicia hoy a las 17:00", vm.previewExact("5", "00", DayPeriod.AUTO))
        assertNull(vm.resolveExact("99", "00", DayPeriod.AUTO))
        assertTrue(vm.previewExact("99", "00", DayPeriod.AUTO).startsWith("Hora no"))
    }
}
