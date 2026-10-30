package app.claudeck.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleLogicTest {
    private val sw = 1080
    private val sh = 2400
    private val head = 150

    @Test fun `la cabeza no se sale de la pantalla`() {
        assertEquals(BubblePoint(0, 0), BubbleLayout.clampHead(-50, -9, sw, sh, head))
        assertEquals(BubblePoint(sw - head, sh - head), BubbleLayout.clampHead(5000, 5000, sw, sh, head))
        assertEquals(BubblePoint(300, 400), BubbleLayout.clampHead(300, 400, sw, sh, head))
    }

    @Test fun `en una pantalla mas chica que la cabeza queda en cero`() {
        assertEquals(BubblePoint(0, 0), BubbleLayout.clampHead(10, 10, 100, 100, head))
    }

    @Test fun `sin posicion guardada arranca a la derecha a un tercio de alto`() {
        assertEquals(BubblePoint(sw - head - 12, 800), BubbleLayout.initialHead(-1, -1, sw, sh, head, 12))
    }

    @Test fun `con posicion guardada la respeta y la corrige si no entra`() {
        assertEquals(BubblePoint(100, 200), BubbleLayout.initialHead(100, 200, sw, sh, head, 12))
        assertEquals(BubblePoint(sw - head, 0), BubbleLayout.initialHead(9999, 0, sw, sh, head, 12))
    }

    @Test fun `arrastre solo cuando se pasa el umbral`() {
        assertFalse(BubbleLayout.isDrag(5f, -5f, 8))
        assertTrue(BubbleLayout.isDrag(9f, 0f, 8))
        assertTrue(BubbleLayout.isDrag(0f, -9f, 8))
    }

    @Test fun `al soltar se pega al borde mas cercano`() {
        assertEquals(12, BubbleLayout.snapTargetX(100, sw, head, 12))
        assertEquals(sw - head - 12, BubbleLayout.snapTargetX(700, sw, head, 12))
    }

    @Test fun `la lista se abre hacia el lado con mas lugar`() {
        assertEquals(BubbleExpansion(right = true, above = true), BubbleLayout.expansion(900, 1800, sw, sh, head))
        assertEquals(BubbleExpansion(right = false, above = false), BubbleLayout.expansion(10, 100, sw, sh, head))
    }

    @Test fun `la ventana abierta se alinea a la cabeza y no se sale`() {
        val e = BubbleExpansion(right = true, above = true)
        val p = BubbleLayout.expandedWindow(900, 1800, head, 18, 600, 700, sw, sh, e)
        assertEquals(900 + head + 18 - 600, p.x)
        assertEquals(1800 + head + 18 - 700, p.y)
        assertEquals(BubblePoint(0, 0), BubbleLayout.expandedWindow(0, 0, head, 0, 600, 700, sw, sh, BubbleExpansion(false, false)))
    }

    @Test fun `ocultar queda lejos de la cabeza`() {
        assertEquals(listOf("x", "a", "b"), BubbleLayout.orderRows(listOf("a", "b"), "x", above = true))
        assertEquals(listOf("a", "b", "x"), BubbleLayout.orderRows(listOf("a", "b"), "x", above = false))
        assertEquals(listOf("b", "a"), BubbleLayout.animationOrder(listOf("a", "b"), above = true))
        assertEquals(listOf("a", "b"), BubbleLayout.animationOrder(listOf("a", "b"), above = false))
    }

    private val now = 1_000_000L
    private val free = BubbleAccount("1", "Libre", 0)
    private val rest = BubbleAccount("2", "Descansa", now + 130 * 60_000L)

    @Test fun `la insignia cuenta libres y cambia de color`() {
        assertEquals(1, BubbleStatus.freeCount(listOf(free, rest), now))
        assertEquals(BubbleStatus.GREEN, BubbleStatus.badgeColor(listOf(free, rest), now))
        assertEquals(BubbleStatus.RED, BubbleStatus.badgeColor(listOf(rest), now))
        assertNull(BubbleStatus.badgeColor(emptyList(), now))
    }

    @Test fun `los textos de la lista`() {
        assertEquals("Libre · Disponible", BubbleStatus.label(free, now))
        assertEquals("Descansa · ⏳ 2 h 10 min", BubbleStatus.label(rest, now))
        assertEquals("#FFFFFF", BubbleStatus.labelColor(free, now))
        assertEquals(BubbleStatus.ORANGE, BubbleStatus.labelColor(rest, now))
    }

    @Test fun `la cuenta actual es la elegida o la primera`() {
        assertEquals("2", BubbleStatus.currentId(listOf(free, rest), "2"))
        assertEquals("1", BubbleStatus.currentId(listOf(free, rest), null))
        assertNull(BubbleStatus.currentId(emptyList(), null))
    }
}
