package app.claudeck.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DockGesturesTest {
    @Test fun `un envion rapido a la izquierda pasa a la cuenta siguiente`() =
        assertEquals(DockGesture.Step(1), DockGestures.fling(-900f, 50f))

    @Test fun `un envion rapido a la derecha pasa a la anterior`() =
        assertEquals(DockGesture.Step(-1), DockGestures.fling(900f, -50f))

    @Test fun `un envion rapido hacia abajo pliega la barra`() =
        assertEquals(DockGesture.SetCollapsed(true), DockGestures.fling(30f, 800f))

    @Test fun `un envion rapido hacia arriba la despliega`() =
        assertEquals(DockGesture.SetCollapsed(false), DockGestures.fling(-30f, -800f))

    @Test fun `un envion lento no hace nada`() {
        assertEquals(DockGesture.None, DockGestures.fling(-500f, 0f))
        assertEquals(DockGesture.None, DockGestures.fling(0f, 400f))
        assertEquals(DockGesture.None, DockGestures.fling(0f, 0f))
    }

    @Test fun `un envion en diagonal gana el eje mas fuerte`() {
        assertEquals(DockGesture.Step(1), DockGestures.fling(-1000f, 900f))
        assertEquals(DockGesture.SetCollapsed(true), DockGestures.fling(700f, 1000f))
    }

    @Test fun `un envion exactamente igual en ambos ejes no hace nada`() =
        assertEquals(DockGesture.None, DockGestures.fling(800f, 800f))

    @Test fun `el arrastre vertical empieza pasada la tolerancia y siendo mas vertical que horizontal`() {
        assertTrue(DockGestures.startsVerticalDrag(dx = 5f, dy = 60f, touchSlop = 16f))
        assertTrue(DockGestures.startsVerticalDrag(dx = 5f, dy = -60f, touchSlop = 16f))
    }

    @Test fun `un movimiento corto o diagonal no empieza el arrastre`() {
        assertFalse(DockGestures.startsVerticalDrag(dx = 0f, dy = 30f, touchSlop = 16f))
        assertFalse(DockGestures.startsVerticalDrag(dx = 50f, dy = 60f, touchSlop = 16f))
        assertFalse(DockGestures.startsVerticalDrag(dx = 0f, dy = 0f, touchSlop = 16f))
    }

    @Test fun `al soltar, hacia abajo pliega y hacia arriba despliega`() {
        assertEquals(true, DockGestures.dragCollapse(80f, 28f))
        assertEquals(false, DockGestures.dragCollapse(-80f, 28f))
    }

    @Test fun `al soltar un arrastre muy corto no hace nada`() {
        assertNull(DockGestures.dragCollapse(20f, 28f))
        assertNull(DockGestures.dragCollapse(-28f, 28f))
    }
}
