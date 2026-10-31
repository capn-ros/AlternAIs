package app.claudeck.domain.usecase

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileRulesTest {
    @Test fun `el tipo sale de la extension sin importar mayusculas`() {
        assertEquals(FileType.IMAGE, FileType.of("Foto.JPG", false))
        assertEquals(FileType.ARCHIVE, FileType.of("a.zip", false))
        assertEquals(FileType.PDF, FileType.of("x.pdf", false))
        assertEquals(FileType.SHEET, FileType.of("datos.csv", false))
        assertEquals(FileType.CODE, FileType.of("Main.kt", false))
        assertEquals(FileType.APK, FileType.of("app.apk", false))
        assertEquals(FileType.TEXT, FileType.of("notas.md", false))
    }

    @Test fun `una carpeta es carpeta aunque tenga extension y lo desconocido es otro`() {
        assertEquals(FileType.FOLDER, FileType.of("fotos.jpg", true))
        assertEquals(FileType.OTHER, FileType.of("sin_extension", false))
        assertEquals(FileType.OTHER, FileType.of("raro.xyz", false))
    }

    @Test fun `renombrar acepta nombres limpios y recorta espacios`() {
        assertEquals("nuevo.txt", FileNames.forRename("  nuevo.txt "))
    }

    @Test fun `renombrar rechaza vacio puntos barras y nulo`() {
        listOf("", "   ", ".", "..", "a/b", "a\u0000b").forEach { assertNull(it, FileNames.forRename(it)) }
    }

    @Test fun `carpeta nueva rechaza lo mismo menos el caracter nulo`() {
        listOf("", ".", "..", "a/b").forEach { assertNull(it, FileNames.forFolder(it)) }
        assertEquals("Mi carpeta", FileNames.forFolder(" Mi carpeta "))
    }

    @Test fun `nombre libre no cambia`() {
        assertEquals("a.txt", FileNames.unique("a.txt") { false })
    }

    @Test fun `nombre ocupado suma numero antes de la extension`() {
        val taken = setOf("a.txt", "a (2).txt")
        assertEquals("a (3).txt", FileNames.unique("a.txt") { it in taken })
    }

    @Test fun `sin extension y con punto inicial`() {
        assertEquals("carpeta (2)", FileNames.unique("carpeta") { it == "carpeta" })
        assertEquals(".env (2)", FileNames.unique(".env") { it == ".env" })
    }

    @Test fun `los ocultos empiezan con punto`() {
        assertTrue(FileNames.isHidden(".git"))
        assertFalse(FileNames.isHidden("git"))
    }

    @Test fun `tamanos legibles`() {
        val l = Locale.US
        assertEquals("0 B", SizeFormat.human(0, l))
        assertEquals("1023 B", SizeFormat.human(1023, l))
        assertEquals("1.0 KB", SizeFormat.human(1024, l))
        assertEquals("1.5 KB", SizeFormat.human(1536, l))
        assertEquals("10 KB", SizeFormat.human(10 * 1024, l))
        assertEquals("5.0 MB", SizeFormat.human(5L * 1024 * 1024, l))
        assertEquals("2.0 GB", SizeFormat.human(2L * 1024 * 1024 * 1024, l))
        assertEquals("1.0 TB", SizeFormat.human(1024L * 1024 * 1024 * 1024, l))
    }
}
