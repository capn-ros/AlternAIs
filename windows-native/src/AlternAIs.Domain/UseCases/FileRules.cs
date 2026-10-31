using System.Globalization;

namespace AlternAIs.Domain.UseCases;

/// <summary>Tipo de archivo según la extensión (decide el ícono y su color en el explorador).</summary>
public enum FileType { Folder, Archive, Image, Video, Audio, Pdf, Doc, Sheet, Code, Exe, Text, Other }

public static class FileTypes
{
    // Mismas listas que el explorador de la versión Electron (fxKind en renderer/app.js).
    private static readonly Dictionary<string, FileType> ByExtension = Build(
        (FileType.Archive, "zip rar 7z tar gz tgz bz2 xz"),
        (FileType.Image, "png jpg jpeg gif webp bmp heic svg ico"),
        (FileType.Video, "mp4 mkv webm avi mov wmv"),
        (FileType.Audio, "mp3 wav ogg flac m4a aac opus"),
        (FileType.Pdf, "pdf"),
        (FileType.Doc, "doc docx odt rtf ppt pptx odp"),
        (FileType.Sheet, "xls xlsx ods csv"),
        (FileType.Code, "js ts kt java py html htm css json xml sh ps1 c cpp h cs go rs yml yaml sql"),
        (FileType.Exe, "exe msi bat cmd apk lnk"),
        (FileType.Text, "txt md log ini cfg"));

    private static Dictionary<string, FileType> Build(params (FileType Type, string List)[] groups)
    {
        var map = new Dictionary<string, FileType>(StringComparer.Ordinal);
        foreach (var (type, list) in groups)
            foreach (var ext in list.Split(' ', StringSplitOptions.RemoveEmptyEntries))
                map[ext] = type;
        return map;
    }

    /// <summary>Un archivo sin extensión (o que es solo «.algo») es «otro».</summary>
    public static FileType Of(string name, bool isDir)
    {
        if (isDir) return FileType.Folder;
        var dot = name.LastIndexOf('.');
        if (dot <= 0 || dot == name.Length - 1) return FileType.Other;
        var ext = name[(dot + 1)..].ToLowerInvariant();
        return ByExtension.TryGetValue(ext, out var t) ? t : FileType.Other;
    }

    /// <summary>Imágenes que Windows sabe mostrar como miniatura.</summary>
    public static bool HasThumbnail(string name)
    {
        var dot = name.LastIndexOf('.');
        if (dot <= 0) return false;
        return name[(dot + 1)..].ToLowerInvariant() is "png" or "jpg" or "jpeg" or "gif" or "bmp" or "webp" or "ico";
    }

    public static bool IsZip(string name) => name.EndsWith(".zip", StringComparison.OrdinalIgnoreCase);
}

/// <summary>Un nombre aceptado o el motivo por el que no sirve.</summary>
public sealed record NameCheck(string? Name, string? Error)
{
    public bool Ok => Name is not null;
}

/// <summary>Reglas de nombres de archivos y carpetas en Windows (mismos mensajes que la versión Electron).</summary>
public static class FileNames
{
    public const string InvalidName = "Nombre no válido";
    public const string InvalidChars = "El nombre no puede llevar  < > : \" / \\ | ? *";
    public const string InvalidEnd = "El nombre no puede terminar en punto o espacio";
    public const string Reserved = "Windows reserva ese nombre";

    private static readonly HashSet<string> ReservedNames = new(StringComparer.OrdinalIgnoreCase)
    {
        "CON", "PRN", "AUX", "NUL",
        "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    };

    /// <summary>Sirve para renombrar y para carpetas nuevas. Se recortan los espacios de los extremos.</summary>
    public static NameCheck Check(string? raw)
    {
        var n = (raw ?? "").Trim();
        if (n.Length == 0 || n == "." || n == "..") return new NameCheck(null, InvalidName);
        foreach (var ch in n)
        {
            if (ch < 32 || "<>:\"/\\|?*".Contains(ch)) return new NameCheck(null, InvalidChars);
        }
        if (n.EndsWith('.') || n.EndsWith(' ')) return new NameCheck(null, InvalidEnd);
        var stem = n.Split('.')[0];
        if (ReservedNames.Contains(stem)) return new NameCheck(null, Reserved);
        return new NameCheck(n, null);
    }

    /// <summary>«nombre (2).ext», «nombre (3).ext»… hasta que <paramref name="exists"/> diga que está libre.</summary>
    public static string Unique(string name, Func<string, bool> exists)
    {
        if (!exists(name)) return name;
        var dot = name.LastIndexOf('.');
        var stem = dot > 0 ? name[..dot] : name;
        var ext = dot > 0 ? name[dot..] : "";
        for (var i = 2; ; i++)
        {
            var candidate = $"{stem} ({i}){ext}";
            if (!exists(candidate)) return candidate;
        }
    }
}

public static class SizeFormat
{
    /// <summary>«512 B», «1,5 KB», «12 MB»… (un decimal si es menor que 10).</summary>
    public static string Human(long bytes, CultureInfo? culture = null)
    {
        var c = culture ?? CultureInfo.CurrentCulture;
        if (bytes < 1024) return bytes.ToString(c) + " B";
        string[] units = ["KB", "MB", "GB", "TB"];
        var v = (double)bytes;
        var i = -1;
        do
        {
            v /= 1024.0;
            i++;
        } while (v >= 1024.0 && i < units.Length - 1);
        return v.ToString(v >= 10 ? "0" : "0.0", c) + " " + units[i];
    }
}

/// <summary>Cómo se ordena una carpeta. Las carpetas siempre van primero.</summary>
public enum FileSort { Name, Date, Size }

/// <summary>Lo mínimo de un elemento de carpeta para ordenarlo.</summary>
public interface IFileItem
{
    string Name { get; }
    bool IsDir { get; }
    long Size { get; }
    long ModifiedMs { get; }
}

public static class FileOrder
{
    /// <summary>Nombre: A→Z sin distinguir mayúsculas. Fecha: lo más nuevo primero. Tamaño: lo más grande primero.</summary>
    public static IReadOnlyList<T> Sort<T>(IEnumerable<T> items, FileSort sort) where T : IFileItem
    {
        var byName = StringComparer.OrdinalIgnoreCase;
        var folders = items.OrderBy(i => i.IsDir ? 0 : 1);
        var sorted = sort switch
        {
            FileSort.Date => folders.ThenByDescending(i => i.ModifiedMs).ThenBy(i => i.Name, byName),
            FileSort.Size => folders.ThenByDescending(i => i.Size).ThenBy(i => i.Name, byName),
            _ => folders.ThenBy(i => i.Name, byName)
        };
        return sorted.ToList();
    }

    public static string Label(FileSort sort) => sort switch
    {
        FileSort.Date => "Fecha",
        FileSort.Size => "Tamaño",
        _ => "Nombre"
    };
}
