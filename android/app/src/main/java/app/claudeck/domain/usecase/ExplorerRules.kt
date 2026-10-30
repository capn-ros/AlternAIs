package app.claudeck.domain.usecase

/** Reglas del explorador de archivos que no dependen de pantallas ni de Android. */
object PickFilter {
    /**
     * ¿Se puede elegir este archivo para subirlo? [accept] son los tipos que pidió la página
     * («image/*», «.pdf», «application/pdf»…); vacío = todo. Las carpetas siempre se muestran.
     * [mimeOf] devuelve el tipo MIME de una extensión (o "" si no se conoce).
     */
    fun accepts(accept: List<String>, fileName: String, isDir: Boolean, mimeOf: (String) -> String): Boolean {
        if (isDir || accept.isEmpty()) return true
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val mime = mimeOf(ext)
        return accept.any { raw ->
            val t = raw.trim().lowercase()
            when {
                t == "*/*" || t == "*" -> true
                t.startsWith(".") -> ext == t.removePrefix(".")
                t.endsWith("/*") -> mime.startsWith(t.removeSuffix("*"))
                else -> mime == t
            }
        }
    }
}

/** Qué pasa al tocar un elemento de la lista. */
enum class ItemAction { OPEN_FOLDER, TOGGLE_SELECTION, ZIP_OPTIONS, OPEN_FILE }

/** Qué pasa al apretar «Atrás» dentro del explorador. */
enum class BackAction { CLEAR_SELECTION, GO_UP, CLOSE }

/** Botones posibles de la barra de acciones. */
enum class BarAction {
    PICK_UPLOAD, DOWNLOADS, STORAGE, SYSTEM_PICKER, CANCEL,
    COPY, MOVE, RENAME, EXTRACT, DELETE, SELECT_ALL, CANCEL_SELECTION,
    PASTE, DISCARD_CLIP, NEW_FOLDER, SORT
}

object ExplorerRules {
    fun itemAction(picking: Boolean, anySelected: Boolean, isDir: Boolean, isZip: Boolean): ItemAction = when {
        picking -> if (isDir) ItemAction.OPEN_FOLDER else ItemAction.TOGGLE_SELECTION
        anySelected -> ItemAction.TOGGLE_SELECTION
        isDir -> ItemAction.OPEN_FOLDER
        isZip -> ItemAction.ZIP_OPTIONS
        else -> ItemAction.OPEN_FILE
    }

    /** Atrás sale de la selección, o sube una carpeta, o (si no queda nada) cierra. */
    fun back(anySelected: Boolean, atRoot: Boolean, hasAccess: Boolean): BackAction = when {
        anySelected -> BackAction.CLEAR_SELECTION
        !atRoot && hasAccess -> BackAction.GO_UP
        else -> BackAction.CLOSE
    }

    /** Botones de la barra según el modo (elegir para subir, selección, portapapeles o normal). */
    fun barActions(
        hasAccess: Boolean, picking: Boolean, selectedCount: Int, onlySelectedIsZip: Boolean, clipCount: Int
    ): List<BarAction> {
        if (!hasAccess) return emptyList()
        if (picking) {
            return buildList {
                if (selectedCount > 0) add(BarAction.PICK_UPLOAD)
                addAll(listOf(BarAction.DOWNLOADS, BarAction.STORAGE, BarAction.SYSTEM_PICKER, BarAction.CANCEL))
            }
        }
        if (selectedCount > 0) {
            val one = selectedCount == 1
            return buildList {
                add(BarAction.COPY)
                add(BarAction.MOVE)
                if (one) add(BarAction.RENAME)
                if (one && onlySelectedIsZip) add(BarAction.EXTRACT)
                addAll(listOf(BarAction.DELETE, BarAction.SELECT_ALL, BarAction.CANCEL_SELECTION))
            }
        }
        return buildList {
            if (clipCount > 0) {
                add(BarAction.PASTE)
                add(BarAction.DISCARD_CLIP)
            }
            addAll(listOf(BarAction.DOWNLOADS, BarAction.STORAGE, BarAction.NEW_FOLDER, BarAction.SORT))
        }
    }

    /** Texto de un botón; [sortLabel] es el orden actual (Fecha, Nombre, Tamaño). */
    fun barLabel(a: BarAction, selectedCount: Int, clipCount: Int, sortLabel: String): String = when (a) {
        BarAction.PICK_UPLOAD -> "Subir ($selectedCount)"
        BarAction.DOWNLOADS -> "Descargas"
        BarAction.STORAGE -> "Almacenamiento"
        BarAction.SYSTEM_PICKER -> "Selector del sistema"
        BarAction.CANCEL, BarAction.CANCEL_SELECTION -> "Cancelar"
        BarAction.COPY -> "Copiar"
        BarAction.MOVE -> "Mover"
        BarAction.RENAME -> "Renombrar"
        BarAction.EXTRACT -> "Extraer"
        BarAction.DELETE -> "Borrar"
        BarAction.SELECT_ALL -> "Todo"
        BarAction.PASTE -> "Pegar aquí ($clipCount)"
        BarAction.DISCARD_CLIP -> "Descartar"
        BarAction.NEW_FOLDER -> "Nueva carpeta"
        BarAction.SORT -> sortLabel
    }

    fun selectionCount(n: Int) = if (n == 1) "1 elemento" else "$n elementos"

    fun title(picking: Boolean, atRoot: Boolean, dirName: String): String {
        val base = if (atRoot) "Almacenamiento" else dirName
        return if (picking) "Subir archivos · $base" else base
    }

    /** Ruta mostrada bajo el título, relativa al almacenamiento ("/" en la raíz). */
    fun relativePath(dirPath: String, rootPath: String): String = dirPath.removePrefix(rootPath).ifEmpty { "/" }

    fun clipHint(cut: Boolean, count: Int) =
        (if (cut) "Para mover: " else "Para copiar: ") + count + ". Andá a la carpeta destino y tocá «Pegar aquí»"

    fun pasteResult(cut: Boolean, count: Int) = (if (cut) "Movidos: " else "Copiados: ") + count

    fun deleteQuestion(count: Int, firstName: String): String {
        val what = if (count == 1) "«$firstName»" else "$count elementos"
        return "Se borra $what para siempre. No se puede deshacer."
    }

    fun deleteResult(deleted: Int, total: Int) = if (deleted == total) "Borrados: $deleted" else "Se borraron $deleted de $total"

    fun renamed(name: String) = "Ahora se llama «$name»"
    fun folderCreated(name: String) = "Carpeta «$name» creada"
    fun extracted(count: Int, folder: String) = "Extraídos $count archivos en «$folder»"
    fun failure(message: String?, type: String) = "No se pudo: " + (message ?: type)

    /** Nombre de la carpeta donde se extrae un zip: el nombre sin «.zip». */
    fun zipFolderName(zipName: String) = zipName.removeSuffix(".zip").removeSuffix(".ZIP")
}
