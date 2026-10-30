package app.claudeck

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Prueba FileOps con carpetas temporales reales (no necesita Android). */
class FileOpsTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun file(dir: File, name: String, text: String = "x", modified: Long? = null): File =
        File(dir, name).apply { parentFile?.mkdirs(); writeText(text); modified?.let { setLastModified(it) } }

    @Test fun `la lista pone carpetas primero y oculta los archivos con punto`() {
        val d = tmp.newFolder()
        file(d, "b.txt"); file(d, "a.txt"); file(d, ".oculto"); File(d, "zeta").mkdir()
        val names = FileOps.list(d, SortMode.NAME)!!.map { it.name }
        assertEquals(listOf("zeta", "a.txt", "b.txt"), names)
    }

    @Test fun `ordenar por tamano va del mas grande al mas chico`() {
        val d = tmp.newFolder()
        file(d, "chico", "a"); file(d, "grande", "a".repeat(100)); file(d, "medio", "a".repeat(10))
        assertEquals(listOf("grande", "medio", "chico"), FileOps.list(d, SortMode.SIZE)!!.map { it.name })
    }

    @Test fun `ordenar por fecha va del mas nuevo al mas viejo`() {
        val d = tmp.newFolder()
        file(d, "viejo", modified = 1_000_000_000L); file(d, "nuevo", modified = 2_000_000_000L)
        assertEquals(listOf("nuevo", "viejo"), FileOps.list(d, SortMode.DATE)!!.map { it.name })
    }

    @Test fun `listar algo que no es carpeta devuelve null`() {
        assertNull(FileOps.list(file(tmp.newFolder(), "f.txt"), SortMode.NAME))
    }

    @Test fun `el orden rota fecha nombre tamano`() {
        assertEquals(SortMode.NAME, SortMode.DATE.next())
        assertEquals(SortMode.SIZE, SortMode.NAME.next())
        assertEquals(SortMode.DATE, SortMode.SIZE.next())
    }

    @Test fun `copiar un archivo que ya existe agrega numero`() {
        val src = tmp.newFolder("src"); val dst = tmp.newFolder("dst")
        val f = file(src, "a.txt", "hola"); file(dst, "a.txt", "otro")
        val copy = FileOps.copy(f, dst)
        assertEquals("a (2).txt", copy.name)
        assertEquals("hola", copy.readText())
        assertEquals("otro", File(dst, "a.txt").readText())
    }

    @Test fun `copiar una carpeta copia todo su contenido`() {
        val src = tmp.newFolder("src"); val dst = tmp.newFolder("dst")
        file(src, "c/x.txt", "1"); file(src, "c/sub/y.txt", "2")
        FileOps.copy(File(src, "c"), dst)
        assertEquals("1", File(dst, "c/x.txt").readText())
        assertEquals("2", File(dst, "c/sub/y.txt").readText())
    }

    @Test fun `no se puede copiar una carpeta dentro de si misma`() {
        val c = tmp.newFolder("c"); val inner = File(c, "in").apply { mkdir() }
        try { FileOps.copy(c, inner); fail() } catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("sí misma")) }
    }

    @Test fun `mover cambia de carpeta y borra el original`() {
        val src = tmp.newFolder("src"); val dst = tmp.newFolder("dst")
        val f = file(src, "m.txt", "dato")
        val moved = FileOps.move(f, dst)
        assertFalse(f.exists())
        assertEquals("dato", moved.readText())
    }

    @Test fun `mover a la misma carpeta no hace nada`() {
        val d = tmp.newFolder(); val f = file(d, "q.txt")
        assertEquals(f, FileOps.move(f, d))
        assertTrue(f.exists())
    }

    @Test fun `no se puede mover una carpeta dentro de si misma`() {
        val c = tmp.newFolder("c"); val inner = File(c, "in").apply { mkdir() }
        try { FileOps.move(c, inner); fail() } catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("sí misma")) }
    }

    @Test fun `isInside reconoce la carpeta misma y las de adentro pero no las hermanas`() {
        val a = tmp.newFolder("a"); val ab = File(a, "b").apply { mkdir() }; val aa = tmp.newFolder("aa")
        assertTrue(FileOps.isInside(a, a))
        assertTrue(FileOps.isInside(ab, a))
        assertFalse(FileOps.isInside(aa, a))
    }

    @Test fun `renombrar valida y no pisa archivos`() {
        val d = tmp.newFolder(); val f = file(d, "a.txt"); file(d, "b.txt")
        assertEquals("c.txt", FileOps.rename(f, " c.txt ").name)
        listOf("", "..", "x/y").forEach {
            try { FileOps.rename(File(d, "c.txt"), it); fail(it) } catch (e: IllegalArgumentException) { assertEquals("Nombre no válido", e.message) }
        }
        try { FileOps.rename(File(d, "c.txt"), "b.txt"); fail() } catch (e: IllegalArgumentException) { assertEquals("Ya existe «b.txt»", e.message) }
    }

    @Test fun `crear carpeta valida y no pisa`() {
        val d = tmp.newFolder()
        assertTrue(FileOps.mkdir(d, " nueva ").isDirectory)
        try { FileOps.mkdir(d, "nueva"); fail() } catch (e: IllegalArgumentException) { assertEquals("Ya existe «nueva»", e.message) }
        try { FileOps.mkdir(d, "a/b"); fail() } catch (e: IllegalArgumentException) { assertEquals("Nombre no válido", e.message) }
    }

    private fun zipOf(dir: File, vararg entries: Pair<String, String?>): File {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            entries.forEach { (n, c) ->
                z.putNextEntry(ZipEntry(n))
                if (c != null) z.write(c.toByteArray())
                z.closeEntry()
            }
        }
        return File(dir, "t.zip").apply { writeBytes(out.toByteArray()) }
    }

    @Test fun `descomprimir extrae archivos y carpetas y cuenta solo archivos`() {
        val d = tmp.newFolder(); val dest = File(d, "out")
        val z = zipOf(d, "a.txt" to "1", "carp/" to null, "carp/b.txt" to "2")
        assertEquals(2, FileOps.unzip(z, dest))
        assertEquals("2", File(dest, "carp/b.txt").readText())
        assertNotNull(File(dest, "a.txt"))
    }

    @Test fun `descomprimir rechaza rutas que se escapan de la carpeta`() {
        val d = tmp.newFolder(); val dest = File(d, "out")
        val z = zipOf(d, "../malo.txt" to "x")
        try { FileOps.unzip(z, dest); fail() } catch (e: SecurityException) { assertTrue(e.message!!.contains("../malo.txt")) }
        assertFalse(File(d, "malo.txt").exists())
    }

    @Test fun `el tipo de archivo trae el color de su icono`() {
        assertEquals(FileKind.FOLDER, FileKind.of("x", true))
        assertEquals(FileKind.PDF, FileKind.of("a.PDF", false))
        assertEquals(0xFFE5484D.toInt(), FileKind.PDF.color)
        assertTrue(FileEntry(File("a.ZIP"), false, 1, 0).isZip)
        assertFalse(FileEntry(File("a.zip"), true, 0, 0).isZip)
    }
}
