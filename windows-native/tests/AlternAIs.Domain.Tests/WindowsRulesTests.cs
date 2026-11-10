using System.Globalization;
using AlternAIs.Domain.Models;
using AlternAIs.Domain.Policies;
using AlternAIs.Domain.UseCases;

namespace AlternAIs.Domain.Tests;

public class FileRulesTests
{
    [Theory]
    [InlineData("foto.JPG", FileType.Image)]
    [InlineData("a.tar.gz", FileType.Archive)]
    [InlineData("instalar.exe", FileType.Exe)]
    [InlineData("script.ps1", FileType.Code)]
    [InlineData("notas.md", FileType.Text)]
    [InlineData("Informe.PDF", FileType.Pdf)]
    [InlineData("sinextension", FileType.Other)]
    [InlineData(".gitignore", FileType.Other)]
    [InlineData("raro.", FileType.Other)]
    [InlineData("x.desconocida", FileType.Other)]
    public void El_tipo_sale_de_la_extension(string name, FileType expected) => Assert.Equal(expected, FileTypes.Of(name, false));

    [Fact] public void Una_carpeta_es_carpeta_aunque_tenga_punto() => Assert.Equal(FileType.Folder, FileTypes.Of("v1.zip", true));

    [Fact]
    public void Miniaturas_y_zip()
    {
        Assert.True(FileTypes.HasThumbnail("a.PNG"));
        Assert.False(FileTypes.HasThumbnail("a.svg"));
        Assert.True(FileTypes.IsZip("cd.ZIP"));
        Assert.False(FileTypes.IsZip("cd.zip.txt"));
    }

    [Theory]
    [InlineData("  bueno.txt ", "bueno.txt")]
    [InlineData("carpeta nueva", "carpeta nueva")]
    [InlineData("mi.archivo.v2.txt", "mi.archivo.v2.txt")]
    public void Nombres_validos_se_recortan(string raw, string expected)
    {
        var r = FileNames.Check(raw);
        Assert.True(r.Ok);
        Assert.Equal(expected, r.Name);
    }

    [Theory]
    [InlineData("", FileNames.InvalidName)]
    [InlineData("..", FileNames.InvalidName)]
    [InlineData("a/b", FileNames.InvalidChars)]
    [InlineData("a:b", FileNames.InvalidChars)]
    [InlineData("que?", FileNames.InvalidChars)]
    [InlineData("termina.", FileNames.InvalidEnd)]
    [InlineData("CON", FileNames.Reserved)]
    [InlineData("nul.txt", FileNames.Reserved)]
    public void Nombres_invalidos_dicen_por_que(string raw, string error)
    {
        var r = FileNames.Check(raw);
        Assert.False(r.Ok);
        Assert.Equal(error, r.Error);
    }

    [Fact]
    public void Nombre_unico_agrega_numero_antes_de_la_extension()
    {
        var taken = new HashSet<string> { "a.txt", "a (2).txt", "carpeta" };
        Assert.Equal("a (3).txt", FileNames.Unique("a.txt", taken.Contains));
        Assert.Equal("carpeta (2)", FileNames.Unique("carpeta", taken.Contains));
        Assert.Equal("libre.txt", FileNames.Unique("libre.txt", taken.Contains));
        Assert.Equal(".env (2)", FileNames.Unique(".env", n => n == ".env"));
    }

    [Fact]
    public void Tamanos_legibles()
    {
        var c = CultureInfo.InvariantCulture;
        Assert.Equal("512 B", SizeFormat.Human(512, c));
        Assert.Equal("1.5 KB", SizeFormat.Human(1536, c));
        Assert.Equal("12 MB", SizeFormat.Human(12L * 1024 * 1024, c));
        Assert.Equal("2.0 GB", SizeFormat.Human(2L * 1024 * 1024 * 1024, c));
    }

    private sealed record Item(string Name, bool IsDir, long Size, long ModifiedMs) : IFileItem;

    [Fact]
    public void Orden_carpetas_primero_y_despues_segun_lo_elegido()
    {
        var items = new[]
        {
            new Item("b.txt", false, 10, 300), new Item("Zeta", true, 0, 100), new Item("a.txt", false, 99, 100),
            new Item("alfa", true, 0, 200)
        };
        Assert.Equal(new[] { "alfa", "Zeta", "a.txt", "b.txt" }, FileOrder.Sort(items, FileSort.Name).Select(i => i.Name));
        Assert.Equal(new[] { "alfa", "Zeta", "b.txt", "a.txt" }, FileOrder.Sort(items, FileSort.Date).Select(i => i.Name));
        Assert.Equal(new[] { "alfa", "Zeta", "a.txt", "b.txt" }, FileOrder.Sort(items, FileSort.Size).Select(i => i.Name));
        Assert.Equal("Tamaño", FileOrder.Label(FileSort.Size));
    }
}

public class ShortcutsTests
{
    [Fact]
    public void La_combinacion_se_arma_igual_que_en_la_pagina()
    {
        Assert.Equal("ctrl+shift+tab", Shortcuts.Combo(true, true, false, "Tab"));
        Assert.Equal("ctrl+n", Shortcuts.Combo(true, false, false, "N"));
        Assert.Equal("ctrl+alt+x", Shortcuts.Combo(true, false, true, "x"));
        Assert.Null(Shortcuts.Combo(false, true, false, "Tab"));
        Assert.Null(Shortcuts.Combo(true, false, false, "Control"));
    }

    [Fact]
    public void Cada_atajo_tiene_su_accion()
    {
        Assert.Equal(new Shortcut(ShortcutAction.NextAccount), Shortcuts.FromCombo("ctrl+tab"));
        Assert.Equal(new Shortcut(ShortcutAction.PreviousAccount), Shortcuts.FromCombo("ctrl+shift+tab"));
        Assert.Equal(new Shortcut(ShortcutAction.ToggleFiles), Shortcuts.FromCombo("ctrl+e"));
        Assert.Equal(new Shortcut(ShortcutAction.ToggleSettings), Shortcuts.FromCombo("ctrl+,"));
        Assert.Equal(new Shortcut(ShortcutAction.SelectAccount, 0), Shortcuts.FromCombo("ctrl+1"));
        Assert.Equal(new Shortcut(ShortcutAction.SelectAccount, 8), Shortcuts.FromCombo("ctrl+9"));
        Assert.Null(Shortcuts.FromCombo("ctrl+0"));
        Assert.Null(Shortcuts.FromCombo("ctrl+c"));
        Assert.Null(Shortcuts.FromCombo(null));
    }

    [Fact]
    public void Todo_lo_que_la_pagina_deja_pasar_es_un_atajo()
    {
        foreach (var c in Shortcuts.PageCombos) Assert.NotNull(Shortcuts.FromCombo(c));
    }

    [Fact]
    public void El_mensaje_de_la_pagina_se_lee_y_lo_demas_se_ignora()
    {
        Assert.Equal("ctrl+tab", Shortcuts.ComboFromMessage("key:ctrl+tab"));
        Assert.Null(Shortcuts.ComboFromMessage("hola"));
        Assert.Null(Shortcuts.ComboFromMessage("key:" + new string('x', 60)));
        Assert.Contains("'ctrl+shift+tab'", Shortcuts.PageScript());
        Assert.Contains("chrome.webview.postMessage", Shortcuts.PageScript());
    }
}

public class WebPermissionTests
{
    [Fact]
    public void Microfono_solo_para_el_propio_servicio_por_https()
    {
        Assert.Equal(PermissionDecision.Allow, WebPermissionPolicy.Decide(WebPermission.Microphone, "https://claude.ai/new", AiService.Claude));
        Assert.Equal(PermissionDecision.Deny, WebPermissionPolicy.Decide(WebPermission.Microphone, "http://claude.ai/", AiService.Claude));
        Assert.Equal(PermissionDecision.Deny, WebPermissionPolicy.Decide(WebPermission.Microphone, "https://claude.ai.evil.com/", AiService.Claude));
        Assert.Equal(PermissionDecision.Deny, WebPermissionPolicy.Decide(WebPermission.Microphone, "https://otro.com/", AiService.Claude));
        Assert.Equal(PermissionDecision.Deny, WebPermissionPolicy.Decide(WebPermission.Microphone, null, AiService.Claude));
    }

    [Fact]
    public void Camara_nunca_y_lo_demas_se_pregunta()
    {
        Assert.Equal(PermissionDecision.Deny, WebPermissionPolicy.Decide(WebPermission.Camera, "https://claude.ai/", AiService.Claude));
        Assert.Equal(PermissionDecision.Allow, WebPermissionPolicy.Decide(WebPermission.Notifications, "https://claude.ai/", AiService.Claude));
        Assert.Equal(PermissionDecision.Ask, WebPermissionPolicy.Decide(WebPermission.ClipboardRead, "https://claude.ai/", AiService.Claude));
    }
}

public class AccountStatusTextTests
{
    private const long Now = 1_800_000_000_000L;
    private const long Hour = 3_600_000L;

    private static Account A(long until = 0, string purpose = "", string email = "") =>
        new("a1", "Trabajo", 1, purpose, LimitUntil: until, Email: email);

    [Fact]
    public void Disponible_y_en_descanso()
    {
        Assert.Equal("Trabajo · Disponible", AccountStatusText.TimeBarText(A(), Now));
        Assert.Equal("Trabajo · faltan 2 h 10 min", AccountStatusText.TimeBarText(A(Now + 2 * Hour + 10 * 60_000), Now));
        Assert.Equal("Trabajo", AccountStatusText.CircleDescription(A(), Now));
        Assert.Equal("Trabajo, en descanso, faltan 5 h 0 min", AccountStatusText.CircleDescription(A(Now + 5 * Hour), Now));
    }

    [Fact]
    public void Linea_completa_con_dia_y_hora()
    {
        // 1_800_000_000_000 ms = 15/01/2027 08:00 UTC (viernes).
        var line = AccountStatusText.InfoLine(A(Now + 3 * Hour, "Código"), Now, TimeZoneInfo.Utc, CultureInfo.InvariantCulture);
        Assert.Equal("Trabajo · Código · En descanso hasta Fri 11:00 (faltan 3 h 0 min)", line);
        Assert.Equal("Trabajo · Disponible", AccountStatusText.InfoLine(A(), Now, TimeZoneInfo.Utc, CultureInfo.InvariantCulture));
    }

    [Fact]
    public void Globo_con_proposito_y_correo()
    {
        Assert.Equal("Trabajo · Disponible\nCódigo · yo@mail.com", AccountStatusText.Tooltip(A(0, "Código", "yo@mail.com"), Now));
        Assert.Equal("Trabajo · Disponible", AccountStatusText.Tooltip(A(), Now));
    }
}

public class LimitNotificationTextTests
{
    [Fact]
    public void Textos_del_aviso()
    {
        Assert.Equal("«Trabajo» ya puede usarse", LimitNotificationText.DoneTitle("Trabajo"));
        Assert.StartsWith("Terminó el descanso. Enviando", LimitNotificationText.DoneText(true));
        Assert.True(LimitNotificationText.OffersReply(false));
        Assert.False(LimitNotificationText.OffersReply(true));
        Assert.Equal("Mensaje enviado a «T»", LimitNotificationText.ResultTitle("T", true));
        Assert.Equal("«hola»", LimitNotificationText.ResultText("hola", true));
        Assert.StartsWith("Copié el mensaje", LimitNotificationText.ResultText("hola", false));
        Assert.EndsWith("…»", LimitNotificationText.ResultText(new string('x', 100), true));
    }

    [Fact]
    public void Botones_con_los_dos_primeros_mensajes()
    {
        var list = LimitNotificationText.SnippetActions(["", "Un mensaje bastante largo para un botón", "tercero"]);
        var only = Assert.Single(list);
        Assert.Equal(1, only.Slot);
        Assert.Equal("Un mensaje bastante la…", only.Label);
        Assert.Equal("Un mensaje bastante largo para un botón", only.Text);
    }

    [Fact]
    public void Lo_escrito_gana_al_mensaje_guardado()
    {
        Assert.Equal("escrito", LimitNotificationText.ReplyText(" escrito ", "guardado"));
        Assert.Equal("guardado", LimitNotificationText.ReplyText(null, "guardado"));
        Assert.Equal("guardado", LimitNotificationText.ReplyText("", "guardado"));
        Assert.Null(LimitNotificationText.ReplyText("  ", null));
    }
}
