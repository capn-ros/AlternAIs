using System.IO.Compression;
using AlternAIs.Domain.UseCases;

namespace AlternAIs.Infrastructure;

/// <summary>Un elemento de una carpeta.</summary>
public sealed record FileEntry(string Name, string FullPath, bool IsDir, long Size, long ModifiedMs) : IFileItem
{
    public FileType Type => FileTypes.Of(Name, IsDir);
}

/// <summary>Un lugar rápido del explorador (Descargas, Escritorio, unidades…).</summary>
public sealed record FilePlace(string Label, string Path);

/// <summary>Error con un mensaje para mostrarle al usuario tal cual.</summary>
public sealed class FileOpException(string message) : Exception(message);

/// <summary>
/// Operaciones del explorador interno (copiar, mover, renombrar, carpeta nueva, extraer .zip). Sin interfaz:
/// se prueban con carpetas temporales. Borrar va a la Papelera y eso lo hace la app (necesita Windows).
/// </summary>
public static class FileOps
{
    private static StringComparison PathComparison =>
        OperatingSystem.IsWindows() ? StringComparison.OrdinalIgnoreCase : StringComparison.Ordinal;

    /// <summary>Contenido de una carpeta. Lo que no se puede leer se omite; los ocultos de sistema no se muestran.</summary>
    public static IReadOnlyList<FileEntry> List(string dir, bool showHidden = false)
    {
        var info = new DirectoryInfo(dir);
        if (!info.Exists) throw new FileOpException("La carpeta no existe");
        var result = new List<FileEntry>();
        IEnumerable<FileSystemInfo> items;
        try { items = info.EnumerateFileSystemInfos(); }
        catch (UnauthorizedAccessException) { throw new FileOpException("Sin permiso para abrir esta carpeta"); }
        try
        {
            foreach (var it in items)
            {
                try
                {
                    var attrs = it.Attributes;
                    if (!showHidden && (attrs.HasFlag(FileAttributes.Hidden) || attrs.HasFlag(FileAttributes.System))) continue;
                    var isDir = attrs.HasFlag(FileAttributes.Directory);
                    var size = isDir ? 0L : ((FileInfo)it).Length;
                    var mtime = new DateTimeOffset(it.LastWriteTimeUtc, TimeSpan.Zero).ToUnixTimeMilliseconds();
                    result.Add(new FileEntry(it.Name, it.FullName, isDir, size, mtime));
                }
                catch (IOException) { }
                catch (UnauthorizedAccessException) { }
            }
        }
        catch (UnauthorizedAccessException) { }
        catch (IOException) { }
        return result;
    }

    /// <summary>Carpeta de arriba, o null si ya es la raíz de la unidad.</summary>
    public static string? Parent(string dir)
    {
        var parent = Directory.GetParent(Path.GetFullPath(dir));
        return parent?.FullName;
    }

    /// <summary>Lugares rápidos: Descargas, Escritorio, Documentos, Imágenes, la carpeta del usuario y las unidades.</summary>
    public static IReadOnlyList<FilePlace> Places()
    {
        var list = new List<FilePlace>();
        var home = Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);
        void Add(string label, string? path)
        {
            if (!string.IsNullOrEmpty(path) && Directory.Exists(path)) list.Add(new FilePlace(label, path));
        }
        Add("Descargas", Path.Combine(home, "Downloads"));
        Add("Escritorio", Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory));
        Add("Documentos", Environment.GetFolderPath(Environment.SpecialFolder.MyDocuments));
        Add("Imágenes", Environment.GetFolderPath(Environment.SpecialFolder.MyPictures));
        Add("Inicio", home);
        try
        {
            foreach (var d in DriveInfo.GetDrives())
            {
                try { if (d.IsReady) list.Add(new FilePlace(d.Name, d.RootDirectory.FullName)); }
                catch (IOException) { }
            }
        }
        catch (IOException) { }
        catch (UnauthorizedAccessException) { }
        return list;
    }

    /// <summary>Carpeta de inicio del explorador: Descargas (o la del usuario si no existe).</summary>
    public static string StartFolder()
    {
        var home = Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);
        var downloads = Path.Combine(home, "Downloads");
        return Directory.Exists(downloads) ? downloads : home;
    }

    /// <summary>¿<paramref name="child"/> es <paramref name="parent"/> o está adentro?</summary>
    public static bool IsInside(string child, string parent)
    {
        var c = Path.GetFullPath(child).TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar);
        var p = Path.GetFullPath(parent).TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar);
        return c.Equals(p, PathComparison) || c.StartsWith(p + Path.DirectorySeparatorChar, PathComparison);
    }

    private static bool Exists(string path) => File.Exists(path) || Directory.Exists(path);

    /// <summary>Un nombre libre dentro de <paramref name="dir"/>: «nombre (2).ext» si ya existe.</summary>
    public static string UniquePath(string dir, string name) =>
        Path.Combine(dir, FileNames.Unique(name, n => Exists(Path.Combine(dir, n))));

    private static string NameOf(string path) =>
        Path.GetFileName(path.TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar));

    /// <summary>Copia un archivo o carpeta dentro de <paramref name="destDir"/> (con «(2)» si ya hay uno igual).</summary>
    public static string CopyInto(string src, string destDir)
    {
        if (Directory.Exists(src))
        {
            if (IsInside(destDir, src)) throw new FileOpException("No se puede copiar una carpeta dentro de sí misma");
            var target = UniquePath(destDir, NameOf(src));
            CopyDirectory(src, target);
            return target;
        }
        if (!File.Exists(src)) throw new FileOpException("Ya no existe «" + NameOf(src) + "»");
        var file = UniquePath(destDir, NameOf(src));
        File.Copy(src, file, overwrite: false);
        return file;
    }

    private static void CopyDirectory(string src, string target)
    {
        Directory.CreateDirectory(target);
        foreach (var f in Directory.EnumerateFiles(src))
            File.Copy(f, Path.Combine(target, Path.GetFileName(f)), overwrite: false);
        foreach (var d in Directory.EnumerateDirectories(src))
            CopyDirectory(d, Path.Combine(target, Path.GetFileName(d)));
    }

    /// <summary>Mueve un archivo o carpeta dentro de <paramref name="destDir"/>. Si ya estaba ahí, no hace nada.</summary>
    public static string MoveInto(string src, string destDir)
    {
        var from = Path.GetDirectoryName(Path.GetFullPath(src).TrimEnd(Path.DirectorySeparatorChar)) ?? "";
        if (from.Equals(Path.GetFullPath(destDir).TrimEnd(Path.DirectorySeparatorChar), PathComparison)) return src;
        var isDir = Directory.Exists(src);
        if (!isDir && !File.Exists(src)) throw new FileOpException("Ya no existe «" + NameOf(src) + "»");
        if (isDir && IsInside(destDir, src)) throw new FileOpException("No se puede mover una carpeta dentro de sí misma");
        var target = UniquePath(destDir, NameOf(src));
        if (isDir)
        {
            try
            {
                Directory.Move(src, target);
            }
            catch (IOException) when (!SameRoot(src, destDir))
            {
                // Otra unidad: Directory.Move no puede, así que se copia y se borra el original.
                CopyDirectory(src, target);
                Directory.Delete(src, recursive: true);
            }
        }
        else
        {
            File.Move(src, target, overwrite: false); // entre unidades File.Move copia y borra solo
        }
        return target;
    }

    private static bool SameRoot(string a, string b) =>
        string.Equals(Path.GetPathRoot(Path.GetFullPath(a)), Path.GetPathRoot(Path.GetFullPath(b)), PathComparison);

    /// <summary>Renombra. Cambiar solo mayúsculas/minúsculas se permite.</summary>
    public static string Rename(string src, string newName)
    {
        var check = FileNames.Check(newName);
        if (!check.Ok) throw new FileOpException(check.Error!);
        var dir = Path.GetDirectoryName(Path.GetFullPath(src).TrimEnd(Path.DirectorySeparatorChar)) ?? throw new FileOpException(FileNames.InvalidName);
        var target = Path.Combine(dir, check.Name!);
        var sameItem = string.Equals(Path.GetFullPath(target), Path.GetFullPath(src), PathComparison);
        if (!sameItem && Exists(target)) throw new FileOpException("Ya existe «" + check.Name + "»");
        if (string.Equals(Path.GetFullPath(target), Path.GetFullPath(src), StringComparison.Ordinal)) return src;
        if (Directory.Exists(src))
        {
            if (sameItem)
            {
                // Solo cambian mayúsculas: Windows necesita un paso intermedio.
                var tmp = src.TrimEnd(Path.DirectorySeparatorChar) + ".~ren" + Environment.TickCount64;
                Directory.Move(src, tmp);
                Directory.Move(tmp, target);
            }
            else
            {
                Directory.Move(src, target);
            }
        }
        else if (File.Exists(src))
        {
            File.Move(src, target, overwrite: sameItem);
        }
        else
        {
            throw new FileOpException("Ya no existe «" + NameOf(src) + "»");
        }
        return target;
    }

    public static string CreateFolder(string parent, string name)
    {
        var check = FileNames.Check(name);
        if (!check.Ok) throw new FileOpException(check.Error!);
        var target = Path.Combine(parent, check.Name!);
        if (Exists(target)) throw new FileOpException("Ya existe «" + check.Name + "»");
        Directory.CreateDirectory(target);
        return target;
    }

    /// <summary>
    /// Extrae un .zip en <paramref name="dest"/> (pisa lo que tenga el mismo nombre). Rechaza el zip entero si alguna
    /// ruta sale de la carpeta («../», rutas absolutas). Devuelve cuántos archivos extrajo.
    /// </summary>
    public static int Extract(string zipPath, string dest)
    {
        if (!FileTypes.IsZip(zipPath)) throw new FileOpException("No es un archivo .zip");
        var root = Path.GetFullPath(dest);
        using var zip = ZipFile.OpenRead(zipPath);
        var plan = new List<(ZipArchiveEntry Entry, string Target, bool IsDir)>();
        foreach (var e in zip.Entries)
        {
            var name = e.FullName.Replace('\\', '/');
            if (name.Length == 0) continue;
            var isDir = name.EndsWith('/');
            var target = Path.GetFullPath(Path.Combine(root, name.TrimEnd('/')));
            if (Path.IsPathRooted(name) || name.Contains(':') || !IsInside(target, root) || target.Equals(root.TrimEnd(Path.DirectorySeparatorChar), PathComparison) && !isDir)
                throw new FileOpException("El zip tiene rutas que salen de la carpeta: no se extrajo nada");
            plan.Add((e, target, isDir));
        }
        Directory.CreateDirectory(root);
        var files = 0;
        foreach (var (entry, target, isDir) in plan)
        {
            if (isDir)
            {
                Directory.CreateDirectory(target);
                continue;
            }
            Directory.CreateDirectory(Path.GetDirectoryName(target)!);
            entry.ExtractToFile(target, overwrite: true);
            files++;
        }
        return files;
    }

    /// <summary>Carpeta para «Extraer en carpeta»: el nombre del zip sin «.zip», libre en la misma carpeta.</summary>
    public static string ExtractFolderFor(string zipPath)
    {
        var dir = Path.GetDirectoryName(Path.GetFullPath(zipPath)) ?? ".";
        var stem = Path.GetFileNameWithoutExtension(zipPath);
        return UniquePath(dir, stem.Length > 0 ? stem : "zip");
    }
}
