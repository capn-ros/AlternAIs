using AlternAIs.Domain.UseCases;

namespace AlternAIs.Domain.Tests;

public class ArchiveRulesTests
{
    private static ArchiveItem F(string p, long size = 1) => new(p, false, size, 0);
    private static ArchiveItem D(string p) => new(p, true, 0, 0);

    [Fact]
    public void Reconoce_zip_y_rar_sin_importar_mayusculas()
    {
        Assert.True(ArchiveRules.IsZip("A.ZIP"));
        Assert.True(ArchiveRules.IsRar("b.Rar"));
        Assert.True(ArchiveRules.IsArchive("c.zip"));
        Assert.False(ArchiveRules.IsArchive("c.7z"));
        Assert.True(ArchiveRules.IsWritable("a.zip"));
        Assert.False(ArchiveRules.IsWritable("a.rar"));
    }

    [Fact]
    public void Normaliza_rutas()
    {
        Assert.Equal("a/b", ArchiveRules.Normalize("a\\b\\"));
        Assert.Equal("a", ArchiveRules.Normalize("./a/"));
        Assert.Equal("", ArchiveRules.Normalize("/"));
    }

    [Theory]
    [InlineData("../x", false)]
    [InlineData("a/../../x", false)]
    [InlineData("/etc/x", false)]
    [InlineData("C:\\x", false)]
    [InlineData("a/b.txt", true)]
    public void Detecta_rutas_inseguras(string path, bool safe) => Assert.Equal(safe, ArchiveRules.IsSafe(path));

    [Fact]
    public void La_raiz_muestra_archivos_sueltos_y_carpetas_inventadas()
    {
        var all = new[] { F("a.txt"), F("fotos/x.jpg"), F("fotos/y/z.jpg"), D("vacia") };
        var c = ArchiveRules.Children(all, "");
        Assert.Equal(new[] { "a.txt", "fotos", "vacia" }, c.Select(i => i.Path));
        Assert.True(c.First(i => i.Path == "fotos").IsDir);
        Assert.True(c.First(i => i.Path == "vacia").IsDir);
    }

    [Fact]
    public void Una_carpeta_muestra_solo_su_nivel()
    {
        var all = new[] { F("a.txt"), F("fotos/x.jpg"), F("fotos/y/z.jpg"), F("otra/q.txt") };
        Assert.Equal(new[] { "fotos/x.jpg", "fotos/y" }, ArchiveRules.Children(all, "fotos").Select(i => i.Path));
        Assert.Equal(new[] { "fotos/y/z.jpg" }, ArchiveRules.Children(all, "fotos/y").Select(i => i.Path));
    }

    [Fact]
    public void La_carpeta_en_si_no_aparece_como_hija()
    {
        var all = new[] { D("fotos"), F("fotos/x.jpg") };
        Assert.Equal(new[] { "fotos/x.jpg" }, ArchiveRules.Children(all, "fotos").Select(i => i.Path));
    }

    [Fact]
    public void Rutas_virtuales()
    {
        var v = ArchiveRules.VirtualPath(@"C:\d\a.zip", "d/x.txt");
        Assert.Equal(@"C:\d\a.zip!/d/x.txt", v);
        Assert.Equal("d/x.txt", ArchiveRules.InnerOf(@"C:\d\a.zip", v));
        Assert.Null(ArchiveRules.InnerOf(@"C:\d\otro.zip", v));
        Assert.Equal("d", ArchiveRules.ParentInner("d/x.txt"));
        Assert.Equal("", ArchiveRules.ParentInner("d"));
    }

    [Fact]
    public void IsUnder_distingue_prefijos_parecidos()
    {
        Assert.True(ArchiveRules.IsUnder("a/b", "a"));
        Assert.True(ArchiveRules.IsUnder("a", "a"));
        Assert.False(ArchiveRules.IsUnder("ab/c", "a"));
    }

    [Fact]
    public void Ruta_mostrada()
    {
        Assert.Equal("x.zip/", ArchiveRules.ShownPath("x.zip", ""));
        Assert.Equal("x.zip/a/b", ArchiveRules.ShownPath("x.zip", "a/b"));
    }
}
