package app.claudeck

import app.claudeck.domain.usecase.ArchiveRules
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Prueba la lectura, extracción y edición de .zip con archivos temporales reales (sin Android). */
class ArchiveOpsTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun zip(vararg entries: Pair<String, String?>): File {
        val f = tmp.newFile("t" + System.nanoTime() + ".zip")
        ZipOutputStream(f.outputStream()).use { z ->
            for ((name, text) in entries) {
                z.putNextEntry(ZipEntry(name))
                if (text != null) z.write(text.toByteArray())
                z.closeEntry()
            }
        }
        return f
    }

    private fun names(zip: File): List<String> = ZipFile(zip).use { zf -> zf.entries().asSequence().map { it.name }.sorted().toList() }

    private fun textOf(zip: File, name: String): String = ZipFile(zip).use { zf -> zf.getInputStream(zf.getEntry(name)).readBytes().decodeToString() }

    @Test fun `lista las entradas con sus rutas`() {
        val z = zip("a.txt" to "hola", "fotos/" to null, "fotos/x.jpg" to "img")
        val l = ArchiveOps.list(z)
        assertEquals(setOf("a.txt", "fotos", "fotos/x.jpg"), l.map { it.path }.toSet())
        assertTrue(l.first { it.path == "fotos" }.isDir)
    }

    @Test fun `extraer todo reproduce la estructura`() {
        val z = zip("a.txt" to "hola", "d/b.txt" to "chau")
        val dest = tmp.newFolder()
        assertEquals(2, ArchiveOps.extract(z, emptyList(), dest))
        assertEquals("chau", File(dest, "d/b.txt").readText())
    }

    @Test fun `extraer una carpeta la deja con su nombre`() {
        val z = zip("x/y/a.txt" to "1", "x/y/b.txt" to "2", "x/z.txt" to "3")
        val dest = tmp.newFolder()
        assertEquals(2, ArchiveOps.extract(z, listOf("x/y"), dest))
        assertTrue(File(dest, "y/a.txt").exists())
        assertFalse(File(dest, "z.txt").exists())
    }

    @Test fun `rechaza entradas que salen de la carpeta`() {
        val z = zip("../afuera.txt" to "mal")
        try {
            ArchiveOps.extract(z, emptyList(), tmp.newFolder())
            fail("debía rechazar la ruta")
        } catch (_: SecurityException) {
        }
    }

    @Test fun `extraer un archivo suelto para abrirlo`() {
        val z = zip("d/nota.txt" to "contenido")
        val f = ArchiveOps.extractOne(z, "d/nota.txt", tmp.newFolder())
        assertEquals("nota.txt", f.name)
        assertEquals("contenido", f.readText())
    }

    @Test fun `borrar un archivo y una carpeta entera`() {
        val z = zip("a.txt" to "1", "d/" to null, "d/b.txt" to "2", "d/e/c.txt" to "3", "z.txt" to "4")
        ArchiveOps.delete(z, listOf("a.txt", "d"))
        assertEquals(listOf("z.txt"), names(z))
    }

    @Test fun `renombrar un archivo conserva el contenido`() {
        val z = zip("d/a.txt" to "hola")
        ArchiveOps.rename(z, "d/a.txt", "b.txt")
        assertEquals(listOf("d/b.txt"), names(z))
        assertEquals("hola", textOf(z, "d/b.txt"))
    }

    @Test fun `renombrar una carpeta cambia todo lo que tiene adentro`() {
        val z = zip("d/" to null, "d/a.txt" to "1", "d/s/b.txt" to "2")
        ArchiveOps.rename(z, "d", "nueva")
        assertEquals(listOf("nueva/", "nueva/a.txt", "nueva/s/b.txt"), names(z))
    }

    @Test fun `renombrar a un nombre existente falla y no toca el zip`() {
        val z = zip("a.txt" to "1", "b.txt" to "2")
        try {
            ArchiveOps.rename(z, "a.txt", "b.txt")
            fail("debía fallar")
        } catch (_: IllegalArgumentException) {
        }
        assertEquals(listOf("a.txt", "b.txt"), names(z))
    }

    @Test fun `crear una carpeta`() {
        val z = zip("a.txt" to "1")
        ArchiveOps.mkdir(z, "", "nueva")
        assertTrue(names(z).contains("nueva/"))
        try {
            ArchiveOps.mkdir(z, "", "nueva")
            fail("debía fallar")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test fun `agregar archivos y carpetas reales`() {
        val z = zip("a.txt" to "1")
        val d = tmp.newFolder()
        val f = File(d, "nuevo.txt").apply { writeText("n") }
        val dir = File(d, "carp").apply { mkdirs() }
        File(dir, "in.txt").writeText("i")
        assertEquals(2, ArchiveOps.add(z, "", listOf(f, dir)))
        assertEquals(listOf("a.txt", "carp/", "carp/in.txt", "nuevo.txt"), names(z))
        assertEquals("i", textOf(z, "carp/in.txt"))
    }

    @Test fun `agregar un nombre repetido lo deja como nombre (2)`() {
        val z = zip("a.txt" to "viejo")
        val f = File(tmp.newFolder(), "a.txt").apply { writeText("nuevo") }
        ArchiveOps.add(z, "", listOf(f))
        assertEquals(listOf("a (2).txt", "a.txt"), names(z))
        assertEquals("viejo", textOf(z, "a.txt"))
    }

    @Test fun `agregar dentro de una carpeta del zip`() {
        val z = zip("d/a.txt" to "1")
        val f = File(tmp.newFolder(), "b.txt").apply { writeText("2") }
        ArchiveOps.add(z, "d", listOf(f))
        assertEquals(listOf("d/a.txt", "d/b.txt"), names(z))
    }

    @Test fun `no queda el archivo temporal`() {
        val z = zip("a.txt" to "1")
        ArchiveOps.delete(z, listOf("a.txt"))
        assertFalse(File(z.parentFile, z.name + ".tmp").exists())
    }

    @Test fun `un rar no se puede modificar`() {
        val rar = tmp.newFile("x.rar")
        try {
            ArchiveOps.delete(rar, listOf("a"))
            fail("debía fallar")
        } catch (_: IllegalArgumentException) {
        }
        assertFalse(ArchiveRules.isWritable(rar.name))
    }
}
