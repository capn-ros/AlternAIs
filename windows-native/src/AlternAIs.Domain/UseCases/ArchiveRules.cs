namespace AlternAIs.Domain.UseCases;

/// <summary>Una entrada de un .zip o .rar. <see cref="Path"/> usa «/», sin barra inicial ni final («fotos/a.jpg»).</summary>
public sealed record ArchiveItem(string Path, bool IsDir, long Size, long ModifiedMs);

/// <summary>Reglas para navegar por dentro de un .zip o .rar como si fuera una carpeta. No dependen de la pantalla.</summary>
public static class ArchiveRules
{
    /// <summary>Separador entre el archivo y la ruta interna en las rutas «virtuales» del explorador.</summary>
    public const string Sep = "!/";

    public const string RarNote = "Los .rar solo se pueden ver y extraer; para modificarlos, extraelos y armá un .zip";

    public static bool IsZip(string name) => name.EndsWith(".zip", StringComparison.OrdinalIgnoreCase);
    public static bool IsRar(string name) => name.EndsWith(".rar", StringComparison.OrdinalIgnoreCase);
    public static bool IsArchive(string name) => IsZip(name) || IsRar(name);

    /// <summary>Los .zip se pueden modificar; los .rar solo se ven y se extraen.</summary>
    public static bool IsWritable(string name) => IsZip(name);

    /// <summary>Ruta interna limpia: barras normales, sin «./», sin barras al principio ni al final.</summary>
    public static string Normalize(string raw)
    {
        var p = raw.Replace('\\', '/');
        while (p.StartsWith("./", StringComparison.Ordinal)) p = p[2..];
        return p.Trim('/');
    }

    /// <summary>Una ruta interna es segura si no sale de la carpeta de destino al extraerla.</summary>
    public static bool IsSafe(string path)
    {
        var p = Normalize(path);
        if (p.Length == 0) return false;
        if (path.StartsWith('/') || path.StartsWith('\\') || (path.Length >= 2 && path[1] == ':')) return false;
        return !p.Split('/').Contains("..");
    }

    public static string VirtualPath(string archivePath, string inner) => archivePath + Sep + inner;

    /// <summary>Ruta interna de una ruta virtual, o null si no pertenece a ese archivo.</summary>
    public static string? InnerOf(string archivePath, string virtualPath)
    {
        var prefix = archivePath + Sep;
        return virtualPath.StartsWith(prefix, StringComparison.OrdinalIgnoreCase) ? virtualPath[prefix.Length..] : null;
    }

    /// <summary>La carpeta de arriba de una ruta interna («a/b» → «a», «a» → «»).</summary>
    public static string ParentInner(string inner)
    {
        var n = Normalize(inner);
        var i = n.LastIndexOf('/');
        return i < 0 ? "" : n[..i];
    }

    /// <summary>¿<paramref name="path"/> es <paramref name="parent"/> o está dentro de ella?</summary>
    public static bool IsUnder(string path, string parent)
    {
        var p = Normalize(path);
        var q = Normalize(parent);
        return p == q || p.StartsWith(q + "/", StringComparison.Ordinal);
    }

    /// <summary>Lo que hay directamente dentro de <paramref name="inner"/> (vacío = raíz). Las carpetas que solo existen como parte de una ruta se inventan.</summary>
    public static IReadOnlyList<ArchiveItem> Children(IEnumerable<ArchiveItem> all, string inner)
    {
        var baseDir = Normalize(inner);
        var prefix = baseDir.Length == 0 ? "" : baseDir + "/";
        var found = new Dictionary<string, ArchiveItem>(StringComparer.Ordinal);
        var order = new List<string>();
        foreach (var item in all)
        {
            var p = Normalize(item.Path);
            if (p.Length == 0 || !p.StartsWith(prefix, StringComparison.Ordinal) || p == baseDir) continue;
            var rest = p[prefix.Length..];
            if (rest.Length == 0) continue;
            var slash = rest.IndexOf('/');
            var first = slash < 0 ? rest : rest[..slash];
            var childPath = prefix + first;
            found.TryGetValue(first, out var current);
            ArchiveItem? next = null;
            if (slash >= 0) { if (current is not { IsDir: true }) next = new ArchiveItem(childPath, true, 0, current?.ModifiedMs ?? item.ModifiedMs); }
            else if (item.IsDir) next = new ArchiveItem(childPath, true, 0, item.ModifiedMs);
            else if (current is not { IsDir: true }) next = new ArchiveItem(childPath, false, item.Size, item.ModifiedMs);
            if (next is null) continue;
            if (current is null) order.Add(first);
            found[first] = next;
        }
        return order.Select(k => found[k]).ToList();
    }

    // ---- Textos
    public static string ShownPath(string archiveName, string inner)
    {
        var n = Normalize(inner);
        return n.Length == 0 ? archiveName + "/" : archiveName + "/" + n;
    }

    public static string Added(int n) => n == 1 ? "Agregado 1 archivo" : "Agregados " + n + " archivos";
    public static string Removed(int n) => n == 1 ? "Quitado del archivo comprimido" : "Quitados " + n + " elementos del archivo comprimido";
    public static string CopiedOut(int n) => "Para copiar: " + n + ". Andá a la carpeta de destino y tocá «Pegar» (Ctrl+V).";
}
