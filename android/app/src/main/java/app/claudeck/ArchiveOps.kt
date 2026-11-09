package app.claudeck

import app.claudeck.domain.usecase.ArchiveItem
import app.claudeck.domain.usecase.ArchiveRules
import app.claudeck.domain.usecase.FileNames
import com.github.junrar.Archive
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Leer, extraer y (solo .zip) modificar archivos comprimidos. Todo puede tardar: llamar desde un hilo aparte.
 * Los .zip se modifican reescribiendo el archivo en uno temporal y reemplazando el original al final.
 */
object ArchiveOps {
    fun list(archive: File): List<ArchiveItem> =
        if (ArchiveRules.isRar(archive.name)) listRar(archive) else listZip(archive)

    private fun listZip(zip: File): List<ArchiveItem> = ZipFile(zip).use { zf ->
        zf.entries().asSequence().mapNotNull { e ->
            val p = ArchiveRules.normalize(e.name)
            if (p.isEmpty()) null else ArchiveItem(p, e.isDirectory, if (e.isDirectory) 0L else maxOf(e.size, 0L), e.time)
        }.toList()
    }

    private fun listRar(rar: File): List<ArchiveItem> = Archive(rar).use { a ->
        a.fileHeaders.mapNotNull { h ->
            val p = ArchiveRules.normalize(h.fileName)
            if (p.isEmpty()) null else ArchiveItem(p, h.isDirectory, if (h.isDirectory) 0L else h.fullUnpackSize, h.mTime?.time ?: 0L)
        }
    }

    private fun canon(f: File): String = try { f.canonicalPath } catch (_: Exception) { f.absolutePath }

    private fun safeTarget(destDir: File, rel: String): File {
        val out = File(destDir, rel)
        val d = canon(destDir)
        val o = canon(out)
        if (o != d && !o.startsWith(d + File.separator)) throw SecurityException("El archivo trae una ruta no permitida: $rel")
        return out
    }

    /**
     * Extrae en [destDir] las entradas [selected] (rutas internas; vacío = todo). Cada elemento elegido
     * queda con su nombre dentro de [destDir] (elegir «a/b» deja «destDir/b/…»). Devuelve cuántos archivos extrajo.
     */
    fun extract(archive: File, selected: List<String>, destDir: File): Int {
        destDir.mkdirs()
        val sel = selected.map { ArchiveRules.normalize(it) }
        // ruta de salida relativa de una entrada, o null si no está elegida
        fun rel(path: String): String? {
            if (sel.isEmpty()) return path
            val root = sel.firstOrNull { ArchiveRules.isUnder(path, it) } ?: return null
            return path.removePrefix(root.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" })
        }
        var count = 0
        if (ArchiveRules.isRar(archive.name)) {
            Archive(archive).use { a ->
                for (h in a.fileHeaders) {
                    val p = ArchiveRules.normalize(h.fileName)
                    val r = rel(p) ?: continue
                    if (!ArchiveRules.isSafe(r)) throw SecurityException("El archivo trae una ruta no permitida: $p")
                    val out = safeTarget(destDir, r)
                    if (h.isDirectory) { out.mkdirs(); continue }
                    out.parentFile?.mkdirs()
                    BufferedOutputStream(FileOutputStream(out)).use { a.extractFile(h, it) }
                    count++
                }
            }
        } else {
            ZipFile(archive).use { zf ->
                for (e in zf.entries()) {
                    val p = ArchiveRules.normalize(e.name)
                    if (p.isEmpty()) continue
                    val r = rel(p) ?: continue
                    if (!ArchiveRules.isSafe(r)) throw SecurityException("El archivo trae una ruta no permitida: ${e.name}")
                    val out = safeTarget(destDir, r)
                    if (e.isDirectory) { out.mkdirs(); continue }
                    out.parentFile?.mkdirs()
                    zf.getInputStream(e).use { ins -> FileOutputStream(out).use { ins.copyTo(it) } }
                    count++
                }
            }
        }
        return count
    }

    /** Extrae una sola entrada (archivo) a una carpeta temporal propia y devuelve el archivo resultante. */
    fun extractOne(archive: File, inner: String, tempRoot: File): File {
        val dir = File(tempRoot, java.lang.Long.toHexString(System.nanoTime()))
        extract(archive, listOf(inner), dir)
        return File(dir, ArchiveRules.normalize(inner).substringAfterLast('/'))
    }

    // ---------------------------------------------------------------- Modificar .zip

    private fun entryName(e: ZipEntry) = ArchiveRules.normalize(e.name)

    private fun copyEntry(zf: ZipFile, e: ZipEntry, out: ZipOutputStream, name: String) {
        val ne = ZipEntry(name)
        if (e.time > 0) ne.time = e.time
        out.putNextEntry(ne)
        if (!e.isDirectory) zf.getInputStream(e).use { it.copyTo(out) }
        out.closeEntry()
    }

    /** Reescribe el zip: [transform] copia lo que quede y agrega lo nuevo; al final reemplaza el original. */
    private fun rewrite(zip: File, transform: (ZipFile, ZipOutputStream) -> Unit) {
        if (!ArchiveRules.isWritable(zip.name)) throw IllegalArgumentException("Este tipo de archivo no se puede modificar")
        val tmp = File(zip.parentFile, zip.name + ".tmp")
        try {
            ZipFile(zip).use { zf ->
                ZipOutputStream(BufferedOutputStream(FileOutputStream(tmp))).use { out -> transform(zf, out) }
            }
            if (!tmp.renameTo(zip)) {
                tmp.copyTo(zip, overwrite = true)
                tmp.delete()
            }
        } catch (t: Throwable) {
            tmp.delete()
            throw t
        }
    }

    /** Copia todas las entradas menos las que [drop] rechaza; [rename] puede cambiarles el nombre. Evita nombres repetidos. */
    private fun copyAll(
        zf: ZipFile, out: ZipOutputStream, written: MutableSet<String>,
        drop: (String) -> Boolean = { false }, rename: (String, Boolean) -> String = { n, _ -> n }
    ) {
        for (e in zf.entries()) {
            val n = entryName(e)
            if (n.isEmpty() || drop(n)) continue
            val newPath = rename(n, e.isDirectory)
            val name = if (e.isDirectory) "$newPath/" else newPath
            if (!written.add(name)) continue
            copyEntry(zf, e, out, name)
        }
    }

    /** Quita entradas (y todo lo que haya dentro, si son carpetas). Devuelve cuántas rutas pidió quitar. */
    fun delete(zip: File, paths: List<String>): Int {
        val ps = paths.map { ArchiveRules.normalize(it) }.filter { it.isNotEmpty() }
        rewrite(zip) { zf, out ->
            copyAll(zf, out, HashSet(), drop = { n -> ps.any { ArchiveRules.isUnder(n, it) } })
        }
        return ps.size
    }

    fun rename(zip: File, path: String, newName: String): String {
        val n = FileNames.forRename(newName) ?: throw IllegalArgumentException("Nombre no válido")
        val old = ArchiveRules.normalize(path)
        val parent = old.substringBeforeLast('/', "")
        val target = if (parent.isEmpty()) n else "$parent/$n"
        val taken = list(zip).any { ArchiveRules.isUnder(it.path, target) }
        if (taken) throw IllegalArgumentException("Ya existe «$n»")
        rewrite(zip) { zf, out ->
            copyAll(zf, out, HashSet(), rename = { p, _ ->
                if (ArchiveRules.isUnder(p, old)) target + p.removePrefix(old) else p
            })
        }
        return n
    }

    fun mkdir(zip: File, parentInner: String, name: String): String {
        val n = FileNames.forFolder(name) ?: throw IllegalArgumentException("Nombre no válido")
        val parent = ArchiveRules.normalize(parentInner)
        val target = if (parent.isEmpty()) n else "$parent/$n"
        if (list(zip).any { ArchiveRules.isUnder(it.path, target) }) throw IllegalArgumentException("Ya existe «$n»")
        rewrite(zip) { zf, out ->
            val written = HashSet<String>()
            copyAll(zf, out, written)
            out.putNextEntry(ZipEntry("$target/"))
            out.closeEntry()
        }
        return n
    }

    /** Agrega archivos y carpetas reales dentro de [parentInner]. Si el nombre ya existe queda «nombre (2)». Devuelve cuántos archivos agregó. */
    fun add(zip: File, parentInner: String, files: List<File>): Int {
        val parent = ArchiveRules.normalize(parentInner)
        val existing = ArchiveRules.children(list(zip), parent).map { it.path.substringAfterLast('/') }.toMutableSet()
        var count = 0
        rewrite(zip) { zf, out ->
            val written = HashSet<String>()
            copyAll(zf, out, written)
            fun put(f: File, path: String) {
                if (f.isDirectory) {
                    if (written.add("$path/")) { out.putNextEntry(ZipEntry("$path/")); out.closeEntry() }
                    f.listFiles()?.sortedBy { it.name }?.forEach { put(it, "$path/${it.name}") }
                } else {
                    val ze = ZipEntry(path)
                    ze.time = f.lastModified()
                    out.putNextEntry(ze)
                    f.inputStream().use { it.copyTo(out) }
                    out.closeEntry()
                    written.add(path)
                    count++
                }
            }
            for (f in files) {
                val name = FileNames.unique(f.name) { it in existing }
                existing.add(name)
                put(f, if (parent.isEmpty()) name else "$parent/$name")
            }
        }
        return count
    }
}
