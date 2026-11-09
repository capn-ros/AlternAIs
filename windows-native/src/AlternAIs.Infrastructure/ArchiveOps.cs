using System.IO.Compression;
using AlternAIs.Domain.UseCases;
using SharpCompress.Archives.Rar;

namespace AlternAIs.Infrastructure;

/// <summary>
/// Leer, extraer y (solo .zip) modificar archivos comprimidos. Los .rar se leen con SharpCompress y son de solo lectura.
/// Todo puede tardar: llamar fuera del hilo de la pantalla.
/// </summary>
public static class ArchiveOps
{
    private delegate void Visit(string path, bool isDir, Func<Stream> open);

    private static long Ms(DateTime? t) => t is null ? 0 : new DateTimeOffset(t.Value.ToUniversalTime(), TimeSpan.Zero).ToUnixTimeMilliseconds();

    public static IReadOnlyList<ArchiveItem> List(string archive)
    {
        if (ArchiveRules.IsRar(archive))
        {
            using var rar = RarArchive.Open(archive);
            var items = new List<ArchiveItem>();
            foreach (var e in rar.Entries)
            {
                var p = ArchiveRules.Normalize(e.Key ?? "");
                if (p.Length > 0) items.Add(new ArchiveItem(p, e.IsDirectory, e.IsDirectory ? 0 : e.Size, Ms(e.LastModifiedTime)));
            }
            return items;
        }
        using var zip = ZipFile.OpenRead(archive);
        var list = new List<ArchiveItem>();
        foreach (var e in zip.Entries)
        {
            var p = ArchiveRules.Normalize(e.FullName);
            if (p.Length == 0) continue;
            var isDir = e.FullName.EndsWith('/') || e.FullName.EndsWith('\\');
            list.Add(new ArchiveItem(p, isDir, isDir ? 0 : e.Length, e.LastWriteTime.ToUnixTimeMilliseconds()));
        }
        return list;
    }

    private static void Walk(string archive, Visit visit)
    {
        if (ArchiveRules.IsRar(archive))
        {
            using var rar = RarArchive.Open(archive);
            foreach (var e in rar.Entries)
            {
                var p = ArchiveRules.Normalize(e.Key ?? "");
                if (p.Length == 0) continue;
                var entry = e;
                visit(p, e.IsDirectory, () => entry.OpenEntryStream());
            }
            return;
        }
        using var zip = ZipFile.OpenRead(archive);
        foreach (var e in zip.Entries)
        {
            var p = ArchiveRules.Normalize(e.FullName);
            if (p.Length == 0) continue;
            var isDir = e.FullName.EndsWith('/') || e.FullName.EndsWith('\\');
            var entry = e;
            visit(p, isDir, () => entry.Open());
        }
    }

    /// <summary>
    /// Extrae en <paramref name="destDir"/> lo elegido (rutas internas; vacío = todo). Cada elemento elegido queda con su
    /// nombre dentro de la carpeta (elegir «a/b» deja «destino/b/…»). Rechaza rutas que salgan de la carpeta. Devuelve cuántos archivos extrajo.
    /// </summary>
    public static int Extract(string archive, IReadOnlyList<string> selected, string destDir)
    {
        Directory.CreateDirectory(destDir);
        var root = Path.GetFullPath(destDir);
        var sel = selected.Select(ArchiveRules.Normalize).Where(s => s.Length > 0).ToList();
        var count = 0;
        Walk(archive, (path, isDir, open) =>
        {
            string rel;
            if (sel.Count == 0) rel = path;
            else
            {
                var match = sel.FirstOrDefault(s => ArchiveRules.IsUnder(path, s));
                if (match is null) return;
                var parent = ArchiveRules.ParentInner(match);
                rel = parent.Length == 0 ? path : path[(parent.Length + 1)..];
            }
            if (!ArchiveRules.IsSafe(rel)) throw new FileOpException("El archivo trae una ruta no permitida: " + path);
            var target = Path.GetFullPath(Path.Combine(root, rel));
            if (!FileOps.IsInside(target, root)) throw new FileOpException("El archivo trae una ruta no permitida: " + path);
            if (isDir)
            {
                Directory.CreateDirectory(target);
                return;
            }
            Directory.CreateDirectory(Path.GetDirectoryName(target)!);
            using var input = open();
            using var output = File.Create(target);
            input.CopyTo(output);
            count++;
        });
        return count;
    }

    /// <summary>Extrae un solo archivo a una carpeta temporal propia y devuelve su ruta.</summary>
    public static string ExtractOne(string archive, string inner, string tempRoot)
    {
        var dir = Path.Combine(tempRoot, Guid.NewGuid().ToString("N"));
        Extract(archive, [inner], dir);
        var n = ArchiveRules.Normalize(inner);
        return Path.Combine(dir, n[(n.LastIndexOf('/') + 1)..]);
    }

    // ---------------------------------------------------------------- Modificar .zip

    private static void RequireWritable(string zip)
    {
        if (!ArchiveRules.IsWritable(zip)) throw new FileOpException("Este tipo de archivo no se puede modificar");
    }

    private static ZipArchive OpenForUpdate(string zip) => ZipFile.Open(zip, ZipArchiveMode.Update);

    private static bool IsDirEntry(ZipArchiveEntry e) => e.FullName.EndsWith('/') || e.FullName.EndsWith('\\');

    /// <summary>Quita entradas (y todo lo que haya dentro, si son carpetas). Devuelve cuántas rutas pidió quitar.</summary>
    public static int Delete(string zip, IReadOnlyList<string> paths)
    {
        RequireWritable(zip);
        var ps = paths.Select(ArchiveRules.Normalize).Where(p => p.Length > 0).ToList();
        using var z = OpenForUpdate(zip);
        foreach (var e in z.Entries.ToList())
        {
            var n = ArchiveRules.Normalize(e.FullName);
            if (ps.Any(p => ArchiveRules.IsUnder(n, p))) e.Delete();
        }
        return ps.Count;
    }

    public static string Rename(string zip, string path, string newName)
    {
        RequireWritable(zip);
        var check = FileNames.Check(newName);
        if (!check.Ok) throw new FileOpException(check.Error!);
        var name = check.Name!;
        var old = ArchiveRules.Normalize(path);
        var parent = ArchiveRules.ParentInner(old);
        var target = parent.Length == 0 ? name : parent + "/" + name;
        if (List(zip).Any(i => ArchiveRules.IsUnder(i.Path, target))) throw new FileOpException("Ya existe «" + name + "»");
        using var z = OpenForUpdate(zip);
        foreach (var e in z.Entries.Where(x => ArchiveRules.IsUnder(x.FullName, old)).ToList())
        {
            var n = ArchiveRules.Normalize(e.FullName);
            var isDir = IsDirEntry(e);
            var ne = z.CreateEntry(target + n[old.Length..] + (isDir ? "/" : ""));
            ne.LastWriteTime = e.LastWriteTime;
            if (!isDir)
            {
                using var src = e.Open();
                using var dst = ne.Open();
                src.CopyTo(dst);
            }
            e.Delete();
        }
        return name;
    }

    public static string CreateFolder(string zip, string parentInner, string name)
    {
        RequireWritable(zip);
        var check = FileNames.Check(name);
        if (!check.Ok) throw new FileOpException(check.Error!);
        var n = check.Name!;
        var parent = ArchiveRules.Normalize(parentInner);
        var target = parent.Length == 0 ? n : parent + "/" + n;
        if (List(zip).Any(i => ArchiveRules.IsUnder(i.Path, target))) throw new FileOpException("Ya existe «" + n + "»");
        using var z = OpenForUpdate(zip);
        z.CreateEntry(target + "/");
        return n;
    }

    /// <summary>Agrega archivos y carpetas reales dentro de <paramref name="parentInner"/>. Si el nombre ya existe queda «nombre (2)». Devuelve cuántos archivos agregó.</summary>
    public static int Add(string zip, string parentInner, IReadOnlyList<string> files)
    {
        RequireWritable(zip);
        var parent = ArchiveRules.Normalize(parentInner);
        var existing = ArchiveRules.Children(List(zip), parent)
            .Select(i => i.Path[(i.Path.LastIndexOf('/') + 1)..])
            .ToHashSet(StringComparer.OrdinalIgnoreCase);
        var count = 0;
        using var z = OpenForUpdate(zip);
        void Put(string source, string entryPath)
        {
            if (Directory.Exists(source))
            {
                z.CreateEntry(entryPath + "/");
                foreach (var child in Directory.EnumerateFileSystemEntries(source).OrderBy(c => c, StringComparer.OrdinalIgnoreCase))
                    Put(child, entryPath + "/" + Path.GetFileName(child));
            }
            else if (File.Exists(source))
            {
                z.CreateEntryFromFile(source, entryPath, CompressionLevel.Optimal);
                count++;
            }
            else throw new FileOpException("Ya no existe «" + Path.GetFileName(source) + "»");
        }
        foreach (var f in files)
        {
            var original = Path.GetFileName(f.TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar));
            var name = FileNames.Unique(original, n => existing.Contains(n));
            existing.Add(name);
            Put(f, parent.Length == 0 ? name : parent + "/" + name);
        }
        return count;
    }
}
