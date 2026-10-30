package app.claudeck.data

import app.claudeck.domain.model.Snippets
import org.junit.Assert.assertEquals
import org.junit.Test

class SnippetsJsonTest {
    @Test fun `sin dato guardado devuelve los de ejemplo`() = assertEquals(Snippets.DEFAULTS, SnippetsJson.decode(null))
    @Test fun `un arreglo vacio guardado sigue vacio`() = assertEquals(emptyList<String>(), SnippetsJson.decode("[]"))
    @Test fun `dato roto devuelve los de ejemplo`() = assertEquals(Snippets.DEFAULTS, SnippetsJson.decode("{roto"))
    @Test fun `ida y vuelta conserva acentos, saltos de linea y comillas`() {
        val list = listOf("Resumí \"esto\"", "línea 1\nlínea 2", "ñandú ✓")
        assertEquals(list, SnippetsJson.decode(SnippetsJson.encode(list)))
    }
    @Test fun `al leer se descartan los vacios`() = assertEquals(listOf("a"), SnippetsJson.decode("""["a","", "  "]"""))
    @Test fun `al guardar se aplican los limites`() {
        val big = (1..80).map { "m$it" }
        assertEquals(50, SnippetsJson.decode(SnippetsJson.encode(big)).size)
    }
}
