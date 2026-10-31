package app.claudeck.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountNavigationTest {
    private val ids = listOf("a", "b", "c")

    @Test fun `siguiente y anterior`() {
        assertEquals("b", AccountNavigation.step(ids, "a", +1))
        assertEquals("a", AccountNavigation.step(ids, "b", -1))
    }

    @Test fun `da la vuelta en los extremos`() {
        assertEquals("a", AccountNavigation.step(ids, "c", +1))
        assertEquals("c", AccountNavigation.step(ids, "a", -1))
    }

    @Test fun `con menos de dos cuentas no hay a donde ir`() {
        assertNull(AccountNavigation.step(emptyList(), null, +1))
        assertNull(AccountNavigation.step(listOf("a"), "a", +1))
    }

    @Test fun `si la actual no esta en la lista se parte desde la primera`() {
        assertEquals("b", AccountNavigation.step(ids, "zzz", +1))
        assertEquals("b", AccountNavigation.step(ids, null, +1))
    }

    @Test fun `con dos cuentas alterna`() {
        assertEquals("b", AccountNavigation.step(listOf("a", "b"), "a", +1))
        assertEquals("a", AccountNavigation.step(listOf("a", "b"), "b", +1))
    }

    @Test fun `el sentido de la animacion sigue el orden de la lista`() {
        assertEquals(1, AccountNavigation.direction(ids, "a", "c"))
        assertEquals(-1, AccountNavigation.direction(ids, "c", "a"))
        assertEquals(0, AccountNavigation.direction(ids, "b", "b"))
    }

    @Test fun `sin cuenta anterior no hay sentido`() {
        assertEquals(0, AccountNavigation.direction(ids, null, "b"))
        assertEquals(0, AccountNavigation.direction(ids, "borrada", "b"))
    }

    @Test fun `la barra se pliega sola solo cuando corresponde`() {
        assertTrue(DockPolicy.shouldAutoHide(tutorialActive = false, collapsed = false, subPanelOpen = false, accountCount = 2))
        assertFalse(DockPolicy.shouldAutoHide(true, false, false, 2))
        assertFalse(DockPolicy.shouldAutoHide(false, true, false, 2))
        assertFalse(DockPolicy.shouldAutoHide(false, false, true, 2))
        assertFalse(DockPolicy.shouldAutoHide(false, false, false, 0))
    }
}
