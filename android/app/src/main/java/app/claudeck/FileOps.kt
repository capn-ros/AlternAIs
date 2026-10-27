package app.claudeck

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipInputStream

enum class SortMode(val label: String) {
    DATE("Fecha"),
    NAME("Nombre"),
    SIZE("Tamaño");

    fun next(): SortMode = values()[(ordinal + 1) % values().size]
}

class FileEntry(val file: File, val isDir: Boolean, val size: Long, val modified: Long) {
    val name: String get() = file.name
    val isZip: Boolean get() = !isDir && file.name.lowercase(Locale.ROOT).endsWith(".zip")
}

/** Operaciones sobre archivos del explorador interno. Todo lo que puede tardar se llama desde un hilo aparte. */
object FileOps {
    /** Carpetas primero y después según el orden elegido. Devuelve null si no se puede leer la carpeta. */
    fun list(dir: File, sort: SortMode): List<FileEntry>? {
        val files = dir.listFiles() ?: return null
        val entries = files
            .filter { !it.name.startsWith(".") }
            .map { FileEntry(it, it.isDirectory, if (it.isDirectory) 0L else it.length(), it.lastModified()) }
        val byKind = compareByDescending<FileEntry> { it.isDir }
        val cmp: Comparator<FileEntry> = when (sort) {
            SortMode.NAME -> byKind.then(compareBy<FileEntry> { it.name.lowercase(Locale.ROOT) })
            SortMode.DATE -> byKind.then(compareByDescending<FileEntry> { it.modified })
            SortMode.SIZE -> byKind.then(compareByDescending<FileEntry> { it.size })
        }
        return entries.sortedWith(cmp)
    }

    /** Un nombre libre dentro de `dir`: «nombre (2).ext» si ya existe. */
    fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 2
        while (true) {
            f = File(dir, "$base ($i)$ext")
            if (!f.exists()) return f
            i++
        }
    }

    private fun canon(f: File): String = try {
        f.canonicalPath
    } catch (_: Exception) {
        f.absolutePath
    }

    /** ¿`child` es `parent` o está dentro de él? */
    fun isInside(child: File, parent: File): Boolean {
        val c = canon(child)
        val p = canon(parent)
        return c == p || c.startsWith(p + File.separator)
    }

    fun copy(src: File, destDir: File): File {
        val target = uniqueFile(destDir, src.name)
        if (src.isDirectory) {
            if (isInside(destDir, src)) throw IllegalArgumentException("No se puede copiar una carpeta dentro de sí misma")
            copyDir(src, target)
        } else {
            src.copyTo(target)
        }
        return target
    }

    private fun copyDir(src: File, target: File) {
        target.mkdirs()
        src.listFiles()?.forEach { c ->
            if (c.isDirectory) copyDir(c, File(target, c.name)) else c.copyTo(File(target, c.name))
        }
    }

    fun move(src: File, destDir: File): File {
        if (canon(src.parentFile ?: destDir) == canon(destDir)) return src // ya está ahí
        if (src.isDirectory && isInside(destDir, src)) throw IllegalArgumentException("No se puede mover una carpeta dentro de sí misma")
        val target = uniqueFile(destDir, src.name)
        if (src.renameTo(target)) return target
        val copied = copy(src, destDir)
        if (!src.deleteRecursively()) throw IllegalStateException("Se copió pero no se pudo borrar el original")
        return copied
    }

    fun rename(f: File, newName: String): File {
        val n = newName.trim()
        if (n.isEmpty() || n == "." || n == ".." || n.contains('/') || n.contains('\u0000')) {
            throw IllegalArgumentException("Nombre no válido")
        }
        val target = File(f.parentFile, n)
        if (target.exists()) throw IllegalArgumentException("Ya existe «$n»")
        if (!f.renameTo(target)) throw IllegalStateException("No se pudo renombrar")
        return target
    }

    fun mkdir(parent: File, name: String): File {
        val n = name.trim()
        if (n.isEmpty() || n == "." || n == ".." || n.contains('/')) throw IllegalArgumentException("Nombre no válido")
        val target = File(parent, n)
        if (target.exists()) throw IllegalArgumentException("Ya existe «$n»")
        if (!target.mkdirs()) throw IllegalStateException("No se pudo crear la carpeta")
        return target
    }

    /** Extrae un .zip en `destDir` (rechaza entradas que salgan de la carpeta). Devuelve cuántos archivos extrajo. */
    fun unzip(zip: File, destDir: File): Int {
        destDir.mkdirs()
        val destCanon = canon(destDir)
        var count = 0
        ZipInputStream(BufferedInputStream(FileInputStream(zip))).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                val out = File(destDir, e.name)
                val oc = canon(out)
                if (oc != destCanon && !oc.startsWith(destCanon + File.separator)) {
                    throw SecurityException("El zip trae una ruta no permitida: ${e.name}")
                }
                if (e.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { zis.copyTo(it) }
                    count++
                }
                zis.closeEntry()
                e = zis.nextEntry
            }
        }
        return count
    }

    fun humanSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var v = bytes.toDouble()
        var i = -1
        do {
            v /= 1024.0
            i++
        } while (v >= 1024.0 && i < units.size - 1)
        return String.format(Locale.getDefault(), if (v >= 10) "%.0f %s" else "%.1f %s", v, units[i])
    }
}
