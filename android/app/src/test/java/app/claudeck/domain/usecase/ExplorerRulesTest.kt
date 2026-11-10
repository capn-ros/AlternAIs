package app.claudeck.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplorerRulesTest {
    private val mimes = mapOf("png" to "image/png", "jpg" to "image/jpeg", "pdf" to "application/pdf")
    private fun ok(accept: List<String>, name: String, dir: Boolean = false) =
        PickFilter.accepts(accept, name, dir) { mimes[it] ?: "" }

    @Test fun `sin filtro se acepta todo y las carpetas siempre`() {
        assertTrue(ok(emptyList(), "a.xyz"))
        assertTrue(ok(listOf("image/*"), "carpeta", dir = true))
    }

    @Test fun `filtro por familia de tipo`() {
        assertTrue(ok(listOf("image/*"), "a.PNG"))
        assertFalse(ok(listOf("image/*"), "a.pdf"))
    }

    @Test fun `filtro por tipo exacto y por extension`() {
        assertTrue(ok(listOf("application/pdf"), "x.pdf"))
        assertFalse(ok(listOf("application/pdf"), "x.png"))
        assertTrue(ok(listOf(".PDF"), "x.pdf"))
        assertFalse(ok(listOf(".zip"), "x.pdf"))
    }

    @Test fun `comodin y varios filtros`() {
        assertTrue(ok(listOf("*/*"), "a.xyz"))
        assertTrue(ok(listOf("*"), "a.xyz"))
        assertTrue(ok(listOf("application/pdf", "image/jpeg"), "f.jpg"))
        assertFalse(ok(listOf(" image/png "), "f.jpg"))
    }

    @Test fun `un archivo de extension desconocida solo pasa por extension o comodin`() {
        assertFalse(ok(listOf("image/*"), "a.raro"))
        assertTrue(ok(listOf(".raro"), "a.raro"))
    }

    @Test fun `tocar un elemento`() {
        assertEquals(ItemAction.OPEN_FOLDER, ExplorerRules.itemAction(false, false, true, false))
        assertEquals(ItemAction.ZIP_OPTIONS, ExplorerRules.itemAction(false, false, false, true))
        assertEquals(ItemAction.OPEN_FILE, ExplorerRules.itemAction(false, false, false, false))
        assertEquals(ItemAction.TOGGLE_SELECTION, ExplorerRules.itemAction(false, true, true, false))
        assertEquals(ItemAction.OPEN_FOLDER, ExplorerRules.itemAction(true, true, true, false))
        assertEquals(ItemAction.TOGGLE_SELECTION, ExplorerRules.itemAction(true, false, false, true))
    }

    @Test fun `atras`() {
        assertEquals(BackAction.CLEAR_SELECTION, ExplorerRules.back(true, false, true))
        assertEquals(BackAction.GO_UP, ExplorerRules.back(false, false, true))
        assertEquals(BackAction.CLOSE, ExplorerRules.back(false, true, true))
        assertEquals(BackAction.CLOSE, ExplorerRules.back(false, false, false))
    }

    @Test fun `la barra sin permiso no tiene botones`() {
        assertTrue(ExplorerRules.barActions(false, false, 0, false, 0).isEmpty())
    }

    @Test fun `la barra normal y con portapapeles`() {
        assertEquals(
            listOf(BarAction.DOWNLOADS, BarAction.STORAGE, BarAction.NEW_FOLDER, BarAction.SORT),
            ExplorerRules.barActions(true, false, 0, false, 0)
        )
        assertEquals(
            listOf(BarAction.PASTE, BarAction.DISCARD_CLIP, BarAction.DOWNLOADS, BarAction.STORAGE, BarAction.NEW_FOLDER, BarAction.SORT),
            ExplorerRules.barActions(true, false, 0, false, 2)
        )
    }

    @Test fun `la barra con seleccion ofrece renombrar y extraer solo con un elemento`() {
        assertEquals(
            listOf(BarAction.COPY, BarAction.MOVE, BarAction.RENAME, BarAction.EXTRACT, BarAction.DELETE, BarAction.SELECT_ALL, BarAction.CANCEL_SELECTION),
            ExplorerRules.barActions(true, false, 1, true, 0)
        )
        assertEquals(
            listOf(BarAction.COPY, BarAction.MOVE, BarAction.DELETE, BarAction.SELECT_ALL, BarAction.CANCEL_SELECTION),
            ExplorerRules.barActions(true, false, 3, true, 0)
        )
    }

    @Test fun `la barra de subir archivos`() {
        assertEquals(
            listOf(BarAction.DOWNLOADS, BarAction.STORAGE, BarAction.SYSTEM_PICKER, BarAction.CANCEL),
            ExplorerRules.barActions(true, true, 0, false, 0)
        )
        assertEquals(BarAction.PICK_UPLOAD, ExplorerRules.barActions(true, true, 2, false, 0).first())
    }

    @Test fun `la barra fija solo tiene Descargas y Almacenamiento`() {
        assertEquals(listOf(BarAction.DOWNLOADS, BarAction.STORAGE), ExplorerRules.placeActions(true))
        assertTrue(ExplorerRules.placeActions(false).isEmpty())
        assertTrue(ExplorerRules.placeActions(true, inArchive = true).isEmpty())
    }

    @Test fun `el boton flotante tiene el resto de las acciones`() {
        assertEquals(
            listOf(BarAction.NEW_FOLDER, BarAction.SORT),
            ExplorerRules.fabActions(true, false, 0, false, 0)
        )
        assertEquals(
            listOf(BarAction.PASTE, BarAction.DISCARD_CLIP, BarAction.NEW_FOLDER, BarAction.SORT),
            ExplorerRules.fabActions(true, false, 0, false, 2)
        )
        assertEquals(
            listOf(BarAction.PICK_UPLOAD, BarAction.SYSTEM_PICKER, BarAction.CANCEL),
            ExplorerRules.fabActions(true, true, 1, false, 0)
        )
    }

    @Test fun `textos de los botones`() {
        assertEquals("Subir (2)", ExplorerRules.barLabel(BarAction.PICK_UPLOAD, 2, 0, "Fecha"))
        assertEquals("Pegar aquí (3)", ExplorerRules.barLabel(BarAction.PASTE, 0, 3, "Fecha"))
        assertEquals("Tamaño", ExplorerRules.barLabel(BarAction.SORT, 0, 0, "Tamaño"))
        assertEquals("Cancelar", ExplorerRules.barLabel(BarAction.CANCEL_SELECTION, 0, 0, ""))
    }

    @Test fun `titulo y ruta`() {
        assertEquals("Almacenamiento", ExplorerRules.title(false, true, "emulated"))
        assertEquals("Fotos", ExplorerRules.title(false, false, "Fotos"))
        assertEquals("Subir archivos · Almacenamiento", ExplorerRules.title(true, true, "x"))
        assertEquals("Subir archivos · Fotos", ExplorerRules.title(true, false, "Fotos"))
        assertEquals("/", ExplorerRules.relativePath("/storage/emulated/0", "/storage/emulated/0"))
        assertEquals("/Download/x", ExplorerRules.relativePath("/storage/emulated/0/Download/x", "/storage/emulated/0"))
    }

    @Test fun `mensajes`() {
        assertEquals("1 elemento", ExplorerRules.selectionCount(1))
        assertEquals("4 elementos", ExplorerRules.selectionCount(4))
        assertEquals("Para mover: 2. Andá a la carpeta destino y tocá «Pegar aquí»", ExplorerRules.clipHint(true, 2))
        assertEquals("Para copiar: 1. Andá a la carpeta destino y tocá «Pegar aquí»", ExplorerRules.clipHint(false, 1))
        assertEquals("Movidos: 3", ExplorerRules.pasteResult(true, 3))
        assertEquals("Copiados: 3", ExplorerRules.pasteResult(false, 3))
        assertEquals("Se borra «a.txt» para siempre. No se puede deshacer.", ExplorerRules.deleteQuestion(1, "a.txt"))
        assertEquals("Se borra 5 elementos para siempre. No se puede deshacer.", ExplorerRules.deleteQuestion(5, "a"))
        assertEquals("Borrados: 2", ExplorerRules.deleteResult(2, 2))
        assertEquals("Se borraron 1 de 3", ExplorerRules.deleteResult(1, 3))
        assertEquals("Ahora se llama «b»", ExplorerRules.renamed("b"))
        assertEquals("Carpeta «n» creada", ExplorerRules.folderCreated("n"))
        assertEquals("Extraídos 4 archivos en «d»", ExplorerRules.extracted(4, "d"))
        assertEquals("No se pudo: boom", ExplorerRules.failure("boom", "X"))
        assertEquals("No se pudo: X", ExplorerRules.failure(null, "X"))
        assertEquals("datos", ExplorerRules.zipFolderName("datos.zip"))
        assertEquals("datos", ExplorerRules.zipFolderName("datos.ZIP"))
    }
}
