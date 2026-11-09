using System.IO.Compression;
using AlternAIs.Domain.UseCases;
using AlternAIs.Infrastructure;

namespace AlternAIs.Application.Tests;

/// <summary>Lectura, extracción y edición de .zip con archivos temporales reales.</summary>
public class ArchiveOpsTests
{
    private static string MakeZip(TempDir t, params (string Name, string? Text)[] entries)
    {
        var path = Path.Combine(t.Path, "t" + Guid.NewGuid().ToString("N") + ".zip");
        using var z = ZipFile.Open(path, ZipArchiveMode.Create);
        foreach (var (name, text) in entries)
        {
            var e = z.CreateEntry(name);
            if (text is not null)
            {
                using var w = new StreamWriter(e.Open());
                w.Write(text);
            }
        }
        return path;
    }

    private static List<string> Names(string zip)
    {
        using var z = ZipFile.OpenRead(zip);
        return z.Entries.Select(e => e.FullName).OrderBy(n => n, StringComparer.Ordinal).ToList();
    }

    private static string TextOf(string zip, string name)
    {
        using var z = ZipFile.OpenRead(zip);
        using var r = new StreamReader(z.GetEntry(name)!.Open());
        return r.ReadToEnd();
    }

    [Fact]
    public void Lista_las_entradas_con_sus_rutas()
    {
        using var t = new TempDir();
        var z = MakeZip(t, ("a.txt", "hola"), ("fotos/", null), ("fotos/x.jpg", "img"));
        var l = ArchiveOps.List(z);
        Assert.Equal(new[] { "a.txt", "fotos", "fotos/x.jpg" }, l.Select(i => i.Path).OrderBy(p => p, StringComparer.Ordinal));
        Assert.True(l.First(i => i.Path == "fotos").IsDir);
    }

    [Fact]
    public void Extraer_todo_reproduce_la_estructura()
    {
        using var t = new TempDir();
        var z = MakeZip(t, ("a.txt", "hola"), ("d/b.txt", "chau"));
        var dest = Path.Combine(t.Path, "out");
        Assert.Equal(2, ArchiveOps.Extract(z, [], dest));
        Assert.Equal("chau", File.ReadAllText(Path.Combine(dest, "d", "b.txt")));
    }

    [Fact]
    public void Extraer_una_carpeta_la_deja_con_su_nombre()
    {
        using var t = new TempDir();
        var z = MakeZip(t, ("x/y/a.txt", "1"), ("x/y/b.txt", "2"), ("x/z.txt", "3"));
        var dest = Path.Combine(t.Path, "out");
        Assert.Equal(2, ArchiveOps.Extract(z, ["x/y"], dest));
        Assert.True(File.Exists(Path.Combine(dest, "y", "a.txt")));
        Assert.False(File.Exists(Path.Combine(dest, "z.txt")));
    }

    [Fact]
    public void Rechaza_entradas_que_salen_de_la_carpeta()
    {
        using var t = new TempDir();
        var z = MakeZip(t, ("../afuera.txt", "mal"));
        Assert.Throws<FileOpException>(() => ArchiveOps.Extract(z, [], Path.Combine(t.Path, "out")));
        Assert.False(File.Exists(Path.Combine(t.Path, "afuera.txt")));
    }

    [Fact]
    public void Extraer_un_archivo_suelto_para_abrirlo()
    {
        using var t = new TempDir();
        var z = MakeZip(t, ("d/nota.txt", "contenido"));
        var f = ArchiveOps.ExtractOne(z, "d/nota.txt", Path.Combine(t.Path, "tmp"));
        Assert.Equal("nota.txt", Path.GetFileName(f));
        Assert.Equal("contenido", File.ReadAllText(f));
    }

    [Fact]
    public void Borrar_un_archivo_y_una_carpeta_entera()
    {
        using var t = new TempDir();
        var z = MakeZip(t, ("a.txt", "1"), ("d/", null), ("d/b.txt", "2"), ("d/e/c.txt", "3"), ("z.txt", "4"));
        ArchiveOps.Delete(z, ["a.txt", "d"]);
        Assert.Equal(new[] { "z.txt" }, Names(z));
    }

    [Fact]
    public void Renombrar_un_archivo_conserva_el_contenido()
    {
        using var t = new TempDir();
        var z = MakeZip(t, ("d/a.txt", "hola"));
        ArchiveOps.Rename(z, "d/a.txt", "b.txt");
        Assert.Equal(new[] { "d/b.txt" }, Names(z));
        Assert.Equal("hola", TextOf(z, "d/b.txt"));
    }

    [Fact]
    public void Renombrar_una_carpeta_cambia_todo_lo_que_tiene_adentro()
    {
        using var t = new TempDir();
        var z = MakeZip(t, ("d/", null), ("d/a.txt", "1"), ("d/s/b.txt", "2"));
        ArchiveOps.Rename(z, "d", "nueva");
        Assert.Equal(new[] { "nueva/", "nueva/a.txt", "nueva/s/b.txt" }, Names(z));
    }

    [Fact]
    public void Renombrar_a_un_nombre_existente_falla_y_no_toca_el_zip()
    {
        using var t = new TempDir();
        var z = MakeZip(t, ("a.txt", "1"), ("b.txt", "2"));
        Assert.Throws<FileOpException>(() => ArchiveOps.Rename(z, "a.txt", "b.txt"));
        Assert.Equal(new[] { "a.txt", "b.txt" }, Names(z));
    }

    [Fact]
    public void Crear_una_carpeta()
    {
        using var t = new TempDir();
        var z = MakeZip(t, ("a.txt", "1"));
        ArchiveOps.CreateFolder(z, "", "nueva");
        Assert.Contains("nueva/", Names(z));
        Assert.Throws<FileOpException>(() => ArchiveOps.CreateFolder(z, "", "nueva"));
    }

    [Fact]
    public void Agregar_archivos_y_carpetas_reales()
    {
        using var t = new TempDir();
        var z = MakeZip(t, ("a.txt", "1"));
        var f = t.File(Path.Combine("src", "nuevo.txt"), "n");
        var dir = t.Dir(Path.Combine("src", "carp"));
        File.WriteAllText(Path.Combine(dir, "in.txt"), "i");
        Assert.Equal(2, ArchiveOps.Add(z, "", [f, dir]));
        Assert.Equal(new[] { "a.txt", "carp/", "carp/in.txt", "nuevo.txt" }, Names(z));
        Assert.Equal("i", TextOf(z, "carp/in.txt"));
    }

    [Fact]
    public void Agregar_un_nombre_repetido_lo_deja_como_nombre_2()
    {
        using var t = new TempDir();
        var z = MakeZip(t, ("a.txt", "viejo"));
        var f = t.File(Path.Combine("src", "a.txt"), "nuevo");
        ArchiveOps.Add(z, "", [f]);
        Assert.Equal(new[] { "a (2).txt", "a.txt" }, Names(z));
        Assert.Equal("viejo", TextOf(z, "a.txt"));
    }

    [Fact]
    public void Agregar_dentro_de_una_carpeta_del_zip()
    {
        using var t = new TempDir();
        var z = MakeZip(t, ("d/a.txt", "1"));
        var f = t.File(Path.Combine("src", "b.txt"), "2");
        ArchiveOps.Add(z, "d", [f]);
        Assert.Equal(new[] { "d/a.txt", "d/b.txt" }, Names(z));
    }

    [Fact]
    public void Un_rar_no_se_puede_modificar()
    {
        using var t = new TempDir();
        var rar = t.File("x.rar", "no es un rar de verdad");
        Assert.Throws<FileOpException>(() => ArchiveOps.Delete(rar, ["a"]));
        Assert.False(ArchiveRules.IsWritable(rar));
    }
}
