package app.claudeck.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveRulesTest {
    private fun f(p: String, size: Long = 1) = ArchiveItem(p, false, size, 0)
    private fun d(p: String) = ArchiveItem(p, true, 0, 0)

    @Test fun `reconoce zip y rar sin importar mayusculas`() {
        assertTrue(ArchiveRules.isZip("A.ZIP"))
        assertTrue(ArchiveRules.isRar("b.Rar"))
        assertTrue(ArchiveRules.isArchive("c.zip"))
        assertFalse(ArchiveRules.isArchive("c.7z"))
        assertTrue(ArchiveRules.isWritable("a.zip"))
        assertFalse(ArchiveRules.isWritable("a.rar"))
    }

    @Test fun `normaliza rutas`() {
        assertEquals("a/b", ArchiveRules.normalize("a\\b\\"))
        assertEquals("a", ArchiveRules.normalize("./a/"))
        assertEquals("", ArchiveRules.normalize("/"))
    }

    @Test fun `detecta rutas inseguras`() {
        assertFalse(ArchiveRules.isSafe("../x"))
        assertFalse(ArchiveRules.isSafe("a/../../x"))
        assertFalse(ArchiveRules.isSafe("/etc/x"))
        assertFalse(ArchiveRules.isSafe("C:\\x"))
        assertTrue(ArchiveRules.isSafe("a/b.txt"))
    }

    @Test fun `la raiz muestra archivos sueltos y carpetas inventadas`() {
        val all = listOf(f("a.txt"), f("fotos/x.jpg"), f("fotos/y/z.jpg"), d("vacia"))
        val c = ArchiveRules.children(all, "")
        assertEquals(listOf("a.txt", "fotos", "vacia"), c.map { it.path })
        assertTrue(c.first { it.path == "fotos" }.isDir)
        assertTrue(c.first { it.path == "vacia" }.isDir)
    }

    @Test fun `una carpeta muestra solo su nivel`() {
        val all = listOf(f("a.txt"), f("fotos/x.jpg"), f("fotos/y/z.jpg"), f("otra/q.txt"))
        assertEquals(listOf("fotos/x.jpg", "fotos/y"), ArchiveRules.children(all, "fotos").map { it.path })
        assertEquals(listOf("fotos/y/z.jpg"), ArchiveRules.children(all, "fotos/y").map { it.path })
    }

    @Test fun `la carpeta en si no aparece como hija`() {
        val all = listOf(d("fotos"), f("fotos/x.jpg"))
        assertEquals(listOf("fotos/x.jpg"), ArchiveRules.children(all, "fotos").map { it.path })
    }

    @Test fun `rutas virtuales`() {
        val v = ArchiveRules.virtualPath("/sd/a.zip", "d/x.txt")
        assertEquals("/sd/a.zip!/d/x.txt", v)
        assertEquals("d/x.txt", ArchiveRules.innerOf("/sd/a.zip", v))
        assertNull(ArchiveRules.innerOf("/sd/otro.zip", v))
        assertEquals("d", ArchiveRules.parentInner("d/x.txt"))
        assertEquals("", ArchiveRules.parentInner("d"))
    }

    @Test fun `isUnder distingue prefijos parecidos`() {
        assertTrue(ArchiveRules.isUnder("a/b", "a"))
        assertTrue(ArchiveRules.isUnder("a", "a"))
        assertFalse(ArchiveRules.isUnder("ab/c", "a"))
    }

    @Test fun `titulos`() {
        assertEquals("x.zip", ArchiveRules.title("x.zip", ""))
        assertEquals("b", ArchiveRules.title("x.zip", "a/b"))
        assertEquals("x.zip/a/b", ArchiveRules.shownPath("x.zip", "a/b"))
        assertEquals("x.zip/", ArchiveRules.shownPath("x.zip", ""))
    }
}
