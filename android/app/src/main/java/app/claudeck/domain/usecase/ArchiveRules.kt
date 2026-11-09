package app.claudeck.domain.usecase

import java.util.Locale

/** Una entrada de un .zip o .rar. [path] usa «/», sin barra final ni inicial (por ejemplo «fotos/a.jpg»). */
data class ArchiveItem(val path: String, val isDir: Boolean, val size: Long, val modified: Long)

/** Reglas para navegar por dentro de un .zip o .rar como si fuera una carpeta. No dependen de Android. */
object ArchiveRules {
    /** Separador entre el archivo y la ruta interna en las rutas «virtuales» del explorador. */
    const val SEP = "!/"

    private fun ext(name: String) = name.substringAfterLast('.', "").lowercase(Locale.ROOT)

    fun isZip(name: String) = ext(name) == "zip"
    fun isRar(name: String) = ext(name) == "rar"
    fun isArchive(name: String) = isZip(name) || isRar(name)

    /** Los .zip se pueden modificar; los .rar solo se ven y se extraen (no hay forma libre de escribirlos). */
    fun isWritable(name: String) = isZip(name)

    /** Ruta interna limpia: barras normales, sin «./», sin barras al principio ni al final. */
    fun normalize(raw: String): String {
        var p = raw.replace('\\', '/')
        while (p.startsWith("./")) p = p.substring(2)
        return p.trim('/')
    }

    /** Una ruta interna es segura si no sale de la carpeta de destino al extraerla. */
    fun isSafe(path: String): Boolean {
        val p = normalize(path)
        if (p.isEmpty()) return false
        if (path.startsWith("/") || path.startsWith("\\") || Regex("^[A-Za-z]:").containsMatchIn(path)) return false
        return p.split('/').none { it == ".." }
    }

    /** Ruta «virtual» de una entrada, para usarla como `File` en la lista del explorador. */
    fun virtualPath(archivePath: String, inner: String): String = archivePath + SEP + inner

    /** Ruta interna de una ruta virtual, o null si no pertenece a ese archivo. */
    fun innerOf(archivePath: String, virtualPath: String): String? {
        val prefix = archivePath + SEP
        return if (virtualPath.startsWith(prefix)) virtualPath.removePrefix(prefix) else null
    }

    /** La carpeta de arriba de una ruta interna («a/b» → «a», «a» → «»). */
    fun parentInner(inner: String): String = normalize(inner).substringBeforeLast('/', "")

    /** Lo que hay directamente dentro de [inner] (vacío = raíz). Las carpetas que solo existen como parte de una ruta se inventan. */
    fun children(all: List<ArchiveItem>, inner: String): List<ArchiveItem> {
        val base = normalize(inner)
        val prefix = if (base.isEmpty()) "" else "$base/"
        val found = LinkedHashMap<String, ArchiveItem>()
        for (item in all) {
            val p = normalize(item.path)
            if (p.isEmpty() || !p.startsWith(prefix) || p == base) continue
            val rest = p.substring(prefix.length)
            if (rest.isEmpty()) continue
            val first = rest.substringBefore('/')
            val childPath = prefix + first
            if (rest.contains('/')) {
                if (found[first]?.isDir != true) found[first] = ArchiveItem(childPath, true, 0L, found[first]?.modified ?: item.modified)
            } else if (item.isDir) {
                found[first] = ArchiveItem(childPath, true, 0L, item.modified)
            } else if (found[first]?.isDir != true) {
                found[first] = ArchiveItem(childPath, false, item.size, item.modified)
            }
        }
        return found.values.toList()
    }

    /** ¿[path] es [parent] o está dentro de ella? */
    fun isUnder(path: String, parent: String): Boolean {
        val p = normalize(path)
        val q = normalize(parent)
        return p == q || p.startsWith("$q/")
    }

    // ---- Textos
    fun title(archiveName: String, inner: String): String {
        val n = normalize(inner)
        return if (n.isEmpty()) archiveName else n.substringAfterLast('/')
    }

    fun shownPath(archiveName: String, inner: String): String {
        val n = normalize(inner)
        return if (n.isEmpty()) "$archiveName/" else "$archiveName/$n"
    }

    const val RAR_NOTE = "Los .rar solo se pueden ver y extraer; para modificarlos, extraelos y armá un .zip"
    fun added(n: Int) = if (n == 1) "Agregado 1 archivo" else "Agregados $n archivos"
    fun removed(n: Int) = if (n == 1) "Quitado del archivo comprimido" else "Quitados $n elementos del archivo comprimido"
    fun extractedTo(n: Int, folder: String) = "Extraídos $n archivos en «$folder»"
    fun copiedOut(n: Int) = "Para copiar: $n. Andá a la carpeta destino y tocá «Pegar aquí»"
}
