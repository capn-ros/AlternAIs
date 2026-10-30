package app.claudeck.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SnippetsTest {
    private val abc = listOf("a", "b", "c")

    @Test fun `agregar suma al final`() = assertEquals(listOf("a", "b", "c", "d"), Snippets.add(abc, "d"))
    @Test fun `agregar un texto vacio o en blanco no cambia nada`() {
        assertEquals(abc, Snippets.add(abc, ""))
        assertEquals(abc, Snippets.add(abc, "   \n"))
    }
    @Test fun `no pasa de 50 mensajes`() {
        val full = (1..50).map { "m$it" }
        assertEquals(full, Snippets.add(full, "uno mas"))
    }
    @Test fun `corta cada mensaje a 4000 caracteres`() =
        assertEquals(4000, Snippets.add(emptyList(), "x".repeat(5000)).single().length)

    @Test fun `reemplazar cambia solo ese indice`() = assertEquals(listOf("a", "X", "c"), Snippets.replace(abc, 1, "X"))
    @Test fun `reemplazar con indice invalido o texto vacio no cambia nada`() {
        assertEquals(abc, Snippets.replace(abc, 9, "X"))
        assertEquals(abc, Snippets.replace(abc, -1, "X"))
        assertEquals(abc, Snippets.replace(abc, 1, " "))
    }

    @Test fun `subir intercambia con el anterior`() = assertEquals(listOf("a", "c", "b"), Snippets.moveUp(abc, 2))
    @Test fun `subir el primero o un indice invalido no cambia nada`() {
        assertEquals(abc, Snippets.moveUp(abc, 0))
        assertEquals(abc, Snippets.moveUp(abc, 7))
    }

    @Test fun `borrar saca ese indice`() = assertEquals(listOf("a", "c"), Snippets.remove(abc, 1))
    @Test fun `borrar con indice invalido no cambia nada`() = assertEquals(abc, Snippets.remove(abc, 3))

    @Test fun `normalizar saca vacios`() = assertEquals(listOf("a", "b"), Snippets.normalize(listOf("a", "", "  ", "b")))
    @Test fun `hay 4 mensajes de ejemplo`() = assertEquals(4, Snippets.DEFAULTS.size)
}
