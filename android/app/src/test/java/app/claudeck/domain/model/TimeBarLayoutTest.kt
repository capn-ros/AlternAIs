package app.claudeck.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeBarLayoutTest {
    @Test fun `la fraccion se limita entre 0 y 1`() {
        assertEquals(0f, TimeBarLayout.clampFraction(-0.5f), 0f)
        assertEquals(0.4f, TimeBarLayout.clampFraction(0.4f), 0f)
        assertEquals(1f, TimeBarLayout.clampFraction(3f), 0f)
    }

    @Test fun `la pastilla tiene las puntas redondas y el texto a la mitad de la altura`() {
        assertEquals(20f, TimeBarLayout.cornerRadius(40f), 0.001f)
        assertEquals(20f, TimeBarLayout.textSize(40f), 0.001f)
    }

    @Test fun `al texto le queda el ancho menos una altura de aire`() =
        assertEquals(260f, TimeBarLayout.textAvailableWidth(300f, 40f), 0.001f)

    @Test fun `el relleno es proporcional y no se pasa del ancho`() {
        assertEquals(150f, TimeBarLayout.fillWidth(300f, 0.5f), 0.001f)
        assertEquals(0f, TimeBarLayout.fillWidth(300f, 0f), 0.001f)
        assertEquals(300f, TimeBarLayout.fillWidth(300f, 2f), 0.001f)
    }

    @Test fun `la linea base centra el texto en vertical`() {
        // ascent negativo y descent positivo, como en Android: -30 y 10 dan una línea base 10 por debajo del centro.
        assertEquals(30f, TimeBarLayout.baseline(40f, -30f, 10f), 0.001f)
    }

    @Test fun `un texto que entra queda igual`() =
        assertEquals("Trabajo · Disponible", TimeBarLayout.ellipsize("Trabajo · Disponible", 20))

    @Test fun `un texto largo se corta, se limpia el espacio del final y lleva puntos suspensivos`() {
        assertEquals("Trabajo ·…", TimeBarLayout.ellipsize("Trabajo · faltan 2 h 10 min", 9))
        assertEquals("Trabajo…", TimeBarLayout.ellipsize("Trabajo · faltan 2 h 10 min", 8))
    }

    @Test fun `si no entra ni una letra queda solo el simbolo`() {
        assertEquals("…", TimeBarLayout.ellipsize("Trabajo", 0))
        assertEquals("…", TimeBarLayout.ellipsize("Trabajo", -3))
    }
}
