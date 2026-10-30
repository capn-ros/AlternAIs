package app.claudeck.ui.accountmenu

import app.claudeck.domain.Clock
import app.claudeck.domain.usecase.AccountMenuAction
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountMenuViewModelTest {
    private val clock = object : Clock {
        override fun now(): Long = 1_000_000L
        override fun zone(): ZoneId = ZoneId.of("UTC")
    }

    @Test fun `arranca sin menu abierto`() = assertNull(AccountMenuViewModel(clock).menu.value)

    @Test fun `abrir arma el menu de esa cuenta con su color`() {
        val vm = AccountMenuViewModel(clock)
        vm.open("abc", 0xFF112233.toInt(), "Casa", "", "", 0L)
        val s = vm.menu.value!!
        assertEquals("abc", s.accountId)
        assertEquals(0xFF112233.toInt(), s.color)
        assertEquals("Casa", s.model.title)
        assertEquals("Disponible", s.model.subtitle)
    }

    @Test fun `usa el reloj inyectado para saber si esta en descanso`() {
        val vm = AccountMenuViewModel(clock)
        vm.open("abc", 0, "Casa", "", "", 2_000_000L)
        assertTrue(vm.menu.value!!.model.items.any { it.action == AccountMenuAction.CLEAR_LIMIT })
        vm.open("abc", 0, "Casa", "", "", 500_000L)
        assertFalse(vm.menu.value!!.model.items.any { it.action == AccountMenuAction.CLEAR_LIMIT })
    }

    @Test fun `cerrar deja el menu en nulo`() {
        val vm = AccountMenuViewModel(clock)
        vm.open("abc", 0, "Casa", "", "", 0L)
        vm.dismiss()
        assertNull(vm.menu.value)
    }
}
