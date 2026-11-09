package app.claudeck

import app.claudeck.domain.usecase.ArchiveRules
import app.claudeck.domain.usecase.FileNames
import app.claudeck.domain.usecase.FileType
import app.claudeck.domain.usecase.SizeFormat
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
    val kind: FileKind get() = FileKind.of(file.name, isDir)
    val isZip: Boolean get() = !isDir && file.name.lowercase(Locale.ROOT).endsWith(".zip")

    /** .zip o .rar: se puede entrar y ver lo que tiene adentro. */
    val isArchive: Boolean get() = !isDir && ArchiveRules.isArchive(file.name)
}

/** Tipo de archivo según la extensión: decide el color del ícono en el explorador. */
enum class FileKind(val color: Int) {
    FOLDER(0xFFF2B01E.toInt()),
    ARCHIVE(0xFFA06BE8.toInt()),
    IMAGE(0xFF2FBF71.toInt()),
    VIDEO(0xFFEF6B3B.toInt()),
    AUDIO(0xFFE85DA8.toInt()),
    PDF(0xFFE5484D.toInt()),
    DOC(0xFF4C8DFF.toInt()),
    SHEET(0xFF3CB7A8.toInt()),
    CODE(0xFF22B8CF.toInt()),
    APK(0xFF7BC043.toInt()),
    TEXT(0xFF9AA3AE.toInt()),
    OTHER(0xFF8A8F98.toInt());

    companion object {
        fun of(name: String, isDir: Boolean): FileKind = valueOf(FileType.of(name, isDir).name)
    }
}

/** Operaciones sobre archivos del explorador interno. Todo lo que puede tardar se llama desde un hilo aparte. */
object FileOps {
    /** Carpetas primero y después según el orden elegido. Devuelve null si no se puede leer la carpeta. */
    fun list(dir: File, sort: SortMode): List<FileEntry>? {
        val files = dir.listFiles() ?: return null
        val entries = files
            .filter { !FileNames.isHidden(it.name) }
            .map { FileEntry(it, it.isDirectory, if (it.isDirectory) 0L else it.length(), it.lastModified()) }
        return sortEntries(entries, sort)
    }

    /** Carpetas primero y después según el orden elegido (también para lo que hay dentro de un .zip). */
    fun sortEntries(entries: List<FileEntry>, sort: SortMode): List<FileEntry> {
        val byKind = compareByDescending<FileEntry> { it.isDir }
        val cmp: Comparator<FileEntry> = when (sort) {
            SortMode.NAME -> byKind.then(compareBy<FileEntry> { it.name.lowercase(Locale.ROOT) })
            SortMode.DATE -> byKind.then(compareByDescending<FileEntry> { it.modified })
            SortMode.SIZE -> byKind.then(compareByDescending<FileEntry> { it.size })
        }
        return entries.sortedWith(cmp)
    }

    /** Un nombre libre dentro de `dir`: «nombre (2).ext» si ya existe. */
    fun uniqueFile(dir: File, name: String): File = File(dir, FileNames.unique(name) { File(dir, it).exists() })

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
        val n = FileNames.forRename(newName) ?: throw IllegalArgumentException("Nombre no válido")
        val target = File(f.parentFile, n)
        if (target.exists()) throw IllegalArgumentException("Ya existe «$n»")
        if (!f.renameTo(target)) throw IllegalStateException("No se pudo renombrar")
        return target
    }

    fun mkdir(parent: File, name: String): File {
        val n = FileNames.forFolder(name) ?: throw IllegalArgumentException("Nombre no válido")
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

    fun humanSize(bytes: Long): String = SizeFormat.human(bytes)
}
