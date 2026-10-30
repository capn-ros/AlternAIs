package app.claudeck.domain.usecase

import java.util.Locale

/** Tipo de archivo según la extensión. */
enum class FileType {
    FOLDER, ARCHIVE, IMAGE, VIDEO, AUDIO, PDF, DOC, SHEET, CODE, APK, TEXT, OTHER;

    companion object {
        fun of(name: String, isDir: Boolean): FileType {
            if (isDir) return FOLDER
            return when (name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
                "zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz" -> ARCHIVE
                "png", "jpg", "jpeg", "gif", "webp", "bmp", "heic", "svg" -> IMAGE
                "mp4", "mkv", "webm", "avi", "mov", "3gp" -> VIDEO
                "mp3", "wav", "ogg", "flac", "m4a", "aac", "opus" -> AUDIO
                "pdf" -> PDF
                "doc", "docx", "odt", "rtf", "ppt", "pptx", "odp" -> DOC
                "xls", "xlsx", "ods", "csv" -> SHEET
                "js", "ts", "kt", "java", "py", "html", "htm", "css", "json", "xml", "sh", "bat", "c", "cpp", "h", "go", "rs", "yml", "yaml", "gradle", "sql" -> CODE
                "apk", "apks", "xapk" -> APK
                "txt", "md", "log", "ini", "cfg" -> TEXT
                else -> OTHER
            }
        }
    }
}

/** Reglas de nombres de archivos y carpetas del explorador. */
object FileNames {
    /** Nombre limpio si es válido para renombrar un archivo o carpeta; null si no. */
    fun forRename(raw: String): String? {
        val n = raw.trim()
        return if (n.isEmpty() || n == "." || n == ".." || n.contains('/') || n.contains('\u0000')) null else n
    }

    /** Nombre limpio si es válido para una carpeta nueva; null si no. */
    fun forFolder(raw: String): String? {
        val n = raw.trim()
        return if (n.isEmpty() || n == "." || n == ".." || n.contains('/')) null else n
    }

    /** «nombre (2).ext», «nombre (3).ext»… hasta que [exists] diga que está libre. */
    fun unique(name: String, exists: (String) -> Boolean): String {
        if (!exists(name)) return name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 2
        while (true) {
            val candidate = "$base ($i)$ext"
            if (!exists(candidate)) return candidate
            i++
        }
    }

    /** Las carpetas y archivos que empiezan con punto no se muestran. */
    fun isHidden(name: String) = name.startsWith(".")
}

object SizeFormat {
    /** «512 B», «1.5 KB», «12 MB»… (un decimal si es menor que 10). */
    fun human(bytes: Long, locale: Locale = Locale.getDefault()): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var v = bytes.toDouble()
        var i = -1
        do {
            v /= 1024.0
            i++
        } while (v >= 1024.0 && i < units.size - 1)
        return String.format(locale, if (v >= 10) "%.0f %s" else "%.1f %s", v, units[i])
    }
}
