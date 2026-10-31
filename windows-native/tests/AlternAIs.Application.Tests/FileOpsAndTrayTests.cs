using System.IO.Compression;
using AlternAIs.Application;
using AlternAIs.Domain.Models;
using AlternAIs.Infrastructure;

namespace AlternAIs.Application.Tests;

public class TrayMenuTests
{
    [Fact]
    public void Sin_cuentas_solo_abrir_y_salir()
    {
        var m = TrayMenu.Build(AppState.Initial, T.Now);
        Assert.Equal("AlternAIs", m.Tooltip);
        Assert.Equal(new[] { TrayEntryKind.Open, TrayEntryKind.Separator, TrayEntryKind.Quit }, m.Entries.Select(e => e.Kind));
    }

    [Fact]
    public void Lista_las_cuentas_de_los_dos_servicios_con_su_estado()
    {
        var state = AppState.Initial with
        {
            Claude = new ServiceBucket([T.Acc("aaaaaaaaaaaa", "Trabajo"), T.Acc("bbbbbbbbbbbb", "Estudio", T.Now + 2 * T.Hour)], "aaaaaaaaaaaa"),
            Replit = new ServiceBucket([T.Acc("cccccccccccc", "Repl")], "cccccccccccc")
        };
        var m = TrayMenu.Build(state, T.Now);
        Assert.Equal("AlternAIs — 2 de 3 disponibles", m.Tooltip);
        var accounts = m.Entries.Where(e => e.Kind == TrayEntryKind.Account).ToList();
        Assert.Equal(3, accounts.Count);
        Assert.Equal("✅  Trabajo — Disponible", accounts[0].Text);
        Assert.Equal("⏳  Estudio — 2 h 0 min", accounts[1].Text);
        Assert.Equal(AiService.Replit, accounts[2].Service);
        Assert.Equal("cccccccccccc", accounts[2].AccountId);
        Assert.Equal(new[] { "Claude", "Replit" }, m.Entries.Where(e => e.Kind == TrayEntryKind.Header).Select(e => e.Text));
        Assert.Equal(TrayEntryKind.Quit, m.Entries[^1].Kind);
    }
}

/// <summary>Carpeta temporal que se borra al final de cada test.</summary>
public sealed class TempDir : IDisposable
{
    public string Path { get; } = System.IO.Path.Combine(System.IO.Path.GetTempPath(), "alternais-test-" + Guid.NewGuid().ToString("N"));

    public TempDir() => Directory.CreateDirectory(Path);

    public string File(string relative, string content = "x")
    {
        var full = System.IO.Path.Combine(Path, relative);
        Directory.CreateDirectory(System.IO.Path.GetDirectoryName(full)!);
        System.IO.File.WriteAllText(full, content);
        return full;
    }

    public string Dir(string relative)
    {
        var full = System.IO.Path.Combine(Path, relative);
        Directory.CreateDirectory(full);
        return full;
    }

    public void Dispose()
    {
        try { Directory.Delete(Path, recursive: true); } catch (IOException) { } catch (UnauthorizedAccessException) { }
    }
}

public class FileOpsTests
{
    [Fact]
    public void Lista_archivos_y_carpetas_con_tamano()
    {
        using var t = new TempDir();
        t.File("a.txt", "hola");
        t.Dir("sub");
        var list = FileOps.List(t.Path);
        Assert.Equal(2, list.Count);
        var file = list.Single(e => e.Name == "a.txt");
        Assert.False(file.IsDir);
        Assert.Equal(4, file.Size);
        Assert.True(list.Single(e => e.Name == "sub").IsDir);
    }

    [Fact]
    public void Carpeta_inexistente_da_un_error_legible()
    {
        var ex = Assert.Throws<FileOpException>(() => FileOps.List(System.IO.Path.Combine(System.IO.Path.GetTempPath(), "no-existe-" + Guid.NewGuid())));
        Assert.Equal("La carpeta no existe", ex.Message);
    }

    [Fact]
    public void Copiar_no_pisa_y_numera()
    {
        using var t = new TempDir();
        var src = t.File("orig/a.txt", "1");
        var dest = t.Dir("dest");
        t.File("dest/a.txt", "viejo");
        var copied = FileOps.CopyInto(src, dest);
        Assert.Equal("a (2).txt", System.IO.Path.GetFileName(copied));
        Assert.Equal("viejo", File.ReadAllText(System.IO.Path.Combine(dest, "a.txt")));
        Assert.True(File.Exists(src));
    }

    [Fact]
    public void Copiar_carpeta_con_contenido()
    {
        using var t = new TempDir();
        t.File("src/x/y.txt", "y");
        var dest = t.Dir("dest");
        var copied = FileOps.CopyInto(System.IO.Path.Combine(t.Path, "src"), dest);
        Assert.Equal("y", File.ReadAllText(System.IO.Path.Combine(copied, "x", "y.txt")));
    }

    [Fact]
    public void No_se_copia_ni_mueve_una_carpeta_dentro_de_si_misma()
    {
        using var t = new TempDir();
        var src = t.Dir("a");
        var inner = t.Dir("a/b");
        Assert.Equal("No se puede copiar una carpeta dentro de sí misma", Assert.Throws<FileOpException>(() => FileOps.CopyInto(src, inner)).Message);
        Assert.Equal("No se puede mover una carpeta dentro de sí misma", Assert.Throws<FileOpException>(() => FileOps.MoveInto(src, inner)).Message);
    }

    [Fact]
    public void Mover_archivo_y_carpeta()
    {
        using var t = new TempDir();
        var file = t.File("a.txt");
        var dir = t.Dir("carpeta");
        t.File("carpeta/dentro.txt");
        var dest = t.Dir("dest");
        var movedFile = FileOps.MoveInto(file, dest);
        var movedDir = FileOps.MoveInto(dir, dest);
        Assert.False(File.Exists(file));
        Assert.True(File.Exists(movedFile));
        Assert.True(File.Exists(System.IO.Path.Combine(movedDir, "dentro.txt")));
        Assert.False(Directory.Exists(dir));
    }

    [Fact]
    public void Mover_a_la_misma_carpeta_no_hace_nada()
    {
        using var t = new TempDir();
        var file = t.File("a.txt");
        Assert.Equal(file, FileOps.MoveInto(file, t.Path));
        Assert.True(File.Exists(file));
    }

    [Fact]
    public void Renombrar_valida_y_no_pisa()
    {
        using var t = new TempDir();
        var a = t.File("a.txt");
        t.File("b.txt");
        Assert.Equal("Ya existe «b.txt»", Assert.Throws<FileOpException>(() => FileOps.Rename(a, "b.txt")).Message);
        Assert.Throws<FileOpException>(() => FileOps.Rename(a, "mal/nombre"));
        var renamed = FileOps.Rename(a, " c.txt ");
        Assert.Equal("c.txt", System.IO.Path.GetFileName(renamed));
        Assert.True(File.Exists(renamed));
    }

    [Fact]
    public void Carpeta_nueva()
    {
        using var t = new TempDir();
        var made = FileOps.CreateFolder(t.Path, "Nueva");
        Assert.True(Directory.Exists(made));
        Assert.Equal("Ya existe «Nueva»", Assert.Throws<FileOpException>(() => FileOps.CreateFolder(t.Path, "Nueva")).Message);
    }

    [Fact]
    public void Extraer_zip_con_carpetas()
    {
        using var t = new TempDir();
        var zipPath = System.IO.Path.Combine(t.Path, "paquete.zip");
        using (var zip = ZipFile.Open(zipPath, ZipArchiveMode.Create))
        {
            using (var w = new StreamWriter(zip.CreateEntry("uno.txt").Open())) w.Write("1");
            using (var w = new StreamWriter(zip.CreateEntry("sub/dos.txt").Open())) w.Write("2");
            zip.CreateEntry("vacia/");
        }
        var dest = FileOps.ExtractFolderFor(zipPath);
        Assert.Equal("paquete", System.IO.Path.GetFileName(dest));
        Assert.Equal(2, FileOps.Extract(zipPath, dest));
        Assert.Equal("2", File.ReadAllText(System.IO.Path.Combine(dest, "sub", "dos.txt")));
        Assert.True(Directory.Exists(System.IO.Path.Combine(dest, "vacia")));
        // La segunda vez la carpeta ya existe: se propone otra.
        Assert.Equal("paquete (2)", System.IO.Path.GetFileName(FileOps.ExtractFolderFor(zipPath)));
    }

    [Fact]
    public void Un_zip_con_rutas_que_salen_no_extrae_nada()
    {
        using var t = new TempDir();
        var zipPath = System.IO.Path.Combine(t.Path, "malo.zip");
        using (var zip = ZipFile.Open(zipPath, ZipArchiveMode.Create))
        {
            using (var w = new StreamWriter(zip.CreateEntry("bien.txt").Open())) w.Write("1");
            using (var w = new StreamWriter(zip.CreateEntry("../fuera.txt").Open())) w.Write("2");
        }
        var dest = System.IO.Path.Combine(t.Path, "dest");
        Assert.Throws<FileOpException>(() => FileOps.Extract(zipPath, dest));
        Assert.False(File.Exists(System.IO.Path.Combine(t.Path, "fuera.txt")));
        Assert.False(File.Exists(System.IO.Path.Combine(dest, "bien.txt")));
    }

    [Fact]
    public void Dentro_de_una_carpeta()
    {
        using var t = new TempDir();
        var a = t.Dir("a");
        Assert.True(FileOps.IsInside(System.IO.Path.Combine(a, "b"), a));
        Assert.True(FileOps.IsInside(a, a));
        Assert.False(FileOps.IsInside(t.Dir("ab"), a));
    }
}
