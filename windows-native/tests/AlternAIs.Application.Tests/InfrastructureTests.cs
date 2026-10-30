using AlternAIs.Application;
using AlternAIs.Domain.Models;
using AlternAIs.Domain.UseCases;
using AlternAIs.Infrastructure;

namespace AlternAIs.Application.Tests;

public class StateJsonTests
{
    // Un accounts.json como lo escribía la versión Electron.
    private const string Electron = """
    {
      "accounts": [
        { "id": "a1b2c3d4e5f6", "name": "Trabajo", "purpose": "Código", "color": "#4FB0C6",
          "limitUntil": 1800000000000, "limitStart": 1799990000000,
          "lastUrl": "https://claude.ai/chat/abc", "email": "yo@mail.com", "autoReply": "Seguí" },
        { "id": "xx", "name": "Mala" },
        { "id": "0123456789ab", "name": "  ", "color": "rojo", "lastUrl": "https://evil.com/" }
      ],
      "selectedId": "0123456789ab",
      "replit": { "accounts": [ { "id": "ffffffffffff", "name": "R", "color": "#81B29A" } ], "selectedId": "nada" },
      "service": "replit",
      "themes": { "claude": "light", "replit": "dark" },
      "tutorialShown": true,
      "snippets": ["uno", "", "dos"],
      "settings": { "railPos": "bottom", "launchAtLogin": true, "closeToTray": false },
      "browser": { "lastUrl": "https://github.com/", "dlMode": "folder", "dlDir": "C:\\Descargas" }
    }
    """;

    [Fact]
    public void Lee_un_accounts_json_de_Electron()
    {
        var s = StateJson.Parse(Electron);
        Assert.Equal(AiService.Replit, s.Service);
        Assert.Equal(2, s.Claude.Accounts.Count);
        var a = s.Claude.Accounts[0];
        Assert.Equal(("a1b2c3d4e5f6", "Trabajo", "Código", "yo@mail.com", "Seguí"), (a.Id, a.Name, a.Purpose, a.Email, a.AutoReply));
        Assert.Equal(unchecked((int)0xFF4FB0C6), a.Color);
        Assert.Equal((1800000000000L, 1799990000000L), (a.LimitUntil, a.LimitStart));
        Assert.Equal("https://claude.ai/chat/abc", a.LastUrl);
    }

    [Fact]
    public void Descarta_ids_invalidos_y_limpia_nombre_color_y_url()
    {
        var b = StateJson.Parse(Electron).Claude.Accounts[1];
        Assert.Equal("0123456789ab", b.Id);
        Assert.Equal("Cuenta", b.Name);
        Assert.Equal(ColorHex.Parse(StateJson.DefaultColorHex), b.Color);
        Assert.Equal("", b.LastUrl);
    }

    [Fact]
    public void Un_id_con_salto_de_linea_al_final_se_descarta()
    {
        // En .NET «$» acepta un salto de línea final; el id acaba en el nombre de la partición, así que no puede pasar.
        var s = StateJson.Parse("""{ "accounts": [ { "id": "a1b2c3d4e5f6\n", "name": "X" } ] }""");
        Assert.Empty(s.Claude.Accounts);
    }

    [Fact]
    public void Un_color_con_salto_de_linea_al_final_vuelve_al_de_fabrica()
    {
        var s = StateJson.Parse("""{ "accounts": [ { "id": "a1b2c3d4e5f6", "name": "X", "color": "#4FB0C6\n" } ] }""");
        Assert.Equal(ColorHex.Parse(StateJson.DefaultColorHex), s.Claude.Accounts.Single().Color);
    }

    [Fact]
    public void Corrige_la_cuenta_activa_que_no_existe()
    {
        var s = StateJson.Parse(Electron);
        Assert.Equal("0123456789ab", s.Claude.SelectedId);
        Assert.Equal("ffffffffffff", s.Replit.SelectedId);
    }

    [Fact]
    public void Lee_temas_ajustes_navegador_y_mensajes()
    {
        var s = StateJson.Parse(Electron);
        Assert.Equal(("light", "dark", true), (s.ClaudeTheme, s.ReplitTheme, s.TutorialShown));
        Assert.Equal(new AppSettings("bottom", true, false), s.Settings);
        Assert.Equal(new BrowserSettings("https://github.com/", "folder", @"C:\Descargas"), s.Browser);
        Assert.Equal(new[] { "uno", "dos" }, s.Snippets.ToArray());
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("no es json")]
    [InlineData("[1,2]")]
    public void Texto_vacio_o_roto_da_el_estado_inicial(string? json)
    {
        var s = StateJson.Parse(json);
        Assert.Empty(s.Claude.Accounts);
        Assert.Equal(AiService.Claude, s.Service);
        Assert.Equal(AlternAIs.Domain.Models.Snippets.Defaults, s.Snippets);
    }

    [Fact]
    public void Valores_invalidos_vuelven_a_los_de_fabrica()
    {
        var s = StateJson.Parse("""{ "service": "otra", "themes": { "claude": "x", "replit": "y" }, "settings": { "railPos": "arriba" }, "browser": { "lastUrl": "ftp://x", "dlMode": "z", "dlDir": "relativa" } }""");
        Assert.Equal(AiService.Claude, s.Service);
        Assert.Equal(("dark", "light"), (s.ClaudeTheme, s.ReplitTheme));
        Assert.Equal("left", s.Settings.RailPos);
        Assert.Equal(new BrowserSettings("", "default", ""), s.Browser);
    }

    [Fact]
    public void Escribir_y_volver_a_leer_da_lo_mismo()
    {
        var original = StateJson.Parse(Electron);
        var again = StateJson.Parse(StateJson.Serialize(original));
        Assert.Equal(original.Claude.Accounts, again.Claude.Accounts);
        Assert.Equal(original.Replit.Accounts, again.Replit.Accounts);
        Assert.Equal(original.Claude.SelectedId, again.Claude.SelectedId);
        Assert.Equal(original.Settings, again.Settings);
        Assert.Equal(original.Browser, again.Browser);
        Assert.Equal(original.Snippets.ToArray(), again.Snippets.ToArray());
    }

    [Fact]
    public void Lo_escrito_usa_las_mismas_claves_que_Electron()
    {
        var json = StateJson.Serialize(StateJson.Parse(Electron));
        foreach (var key in new[] { "\"accounts\"", "\"selectedId\"", "\"replit\"", "\"service\"", "\"themes\"", "\"tutorialShown\"", "\"snippets\"", "\"settings\"", "\"browser\"", "\"limitUntil\"", "\"autoReply\"", "\"railPos\"", "\"dlMode\"" })
            Assert.Contains(key, json);
        Assert.Contains("\"color\": \"#4FB0C6\"", json);
        Assert.Contains("\"service\": \"replit\"", json);
    }
}

public class ExportJsonTests
{
    private static AppState Sample() => AppState.Initial
        .WithBucket(AiService.Claude, new ServiceBucket(
            [new Account("a1b2c3d4e5f6", "Trabajo", unchecked((int)0xFF4FB0C6), "Código", T.Now + T.Hour, T.Now, "https://claude.ai/chat/1", "yo@mail.com", "secreto")],
            "a1b2c3d4e5f6"));

    [Fact]
    public void Exportar_no_lleva_ids_ni_mensaje_automatico()
    {
        var json = ExportJson.Build(Sample(), T.Now);
        Assert.DoesNotContain("a1b2c3d4e5f6", json);
        Assert.DoesNotContain("secreto", json);
        Assert.Contains("\"app\": \"AlternAIs\"", json);
        Assert.Contains("\"version\": 1", json);
        Assert.Equal(1, ExportJson.CountAccounts(Sample()));
    }

    [Fact]
    public void Exportar_e_importar_conserva_los_datos()
    {
        var parsed = ExportJson.Parse(ExportJson.Build(Sample(), T.Now))!;
        var a = parsed[AiService.Claude].Single();
        Assert.Equal(("Trabajo", "Código", "#4FB0C6", "yo@mail.com", true), (a.Name, a.Purpose, a.ColorHex, a.Email, a.Selected));
        Assert.Equal((T.Now + T.Hour, T.Now), (a.LimitUntil, a.LimitStart));
        Assert.Empty(parsed[AiService.Replit]);
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("basura")]
    [InlineData("{}")]
    [InlineData("""{"services": 5}""")]
    public void Un_archivo_invalido_da_null(string? json) => Assert.Null(ExportJson.Parse(json));

    [Fact]
    public void Un_archivo_demasiado_grande_da_null() =>
        Assert.Null(ExportJson.Parse("{\"services\":{}}" + new string(' ', ExportJson.MaxChars)));

    [Fact]
    public void Importa_solo_hasta_50_por_servicio()
    {
        var items = string.Join(",", Enumerable.Range(0, 60).Select(i => $"{{\"name\":\"c{i}\"}}"));
        var parsed = ExportJson.Parse($"{{\"services\":{{\"claude\":{{\"accounts\":[{items}]}}}}}}")!;
        Assert.Equal(50, parsed[AiService.Claude].Count);
    }

    [Fact]
    public void Un_archivo_exportado_se_puede_importar_en_el_servicio()
    {
        var store = new MemoryStore();
        var svc = new AccountsService(store, new FakeClock(T.Now), new IdSource().Next);
        var summary = svc.Import(ExportJson.Parse(ExportJson.Build(Sample(), T.Now))!);
        Assert.Equal(1, summary.Added);
        Assert.Equal("Trabajo", svc.State.Claude.Accounts.Single().Name);
    }
}

public class JsonFileStateStoreTests : IDisposable
{
    private readonly string _dir = Path.Combine(Path.GetTempPath(), "alternais-" + Guid.NewGuid().ToString("N"));

    public void Dispose()
    {
        try { Directory.Delete(_dir, true); } catch (IOException) { }
    }

    [Fact]
    public void Si_no_hay_archivo_devuelve_el_estado_inicial()
    {
        var store = new JsonFileStateStore(Path.Combine(_dir, "accounts.json"));
        Assert.Empty(store.Load().Claude.Accounts);
    }

    [Fact]
    public void Guarda_crea_la_carpeta_y_se_puede_volver_a_leer()
    {
        var path = Path.Combine(_dir, "sub", "accounts.json");
        var store = new JsonFileStateStore(path);
        var svc = new AccountsService(store, new FakeClock(T.Now), new IdSource().Next);
        svc.Add(new AccountFormValid("Trabajo", "", "", 5));
        Assert.True(File.Exists(path));
        Assert.False(File.Exists(path + ".tmp"));
        Assert.Equal("Trabajo", new JsonFileStateStore(path).Load().Claude.Accounts.Single().Name);
    }

    [Fact]
    public void Un_archivo_roto_no_tira_error()
    {
        Directory.CreateDirectory(_dir);
        var path = Path.Combine(_dir, "accounts.json");
        File.WriteAllText(path, "{ roto");
        Assert.Empty(new JsonFileStateStore(path).Load().Claude.Accounts);
    }

    [Fact]
    public void La_ruta_por_defecto_es_la_de_Claudeck()
    {
        var p = JsonFileStateStore.DefaultPath();
        Assert.EndsWith(Path.Combine("Claudeck", "accounts.json"), p);
    }
}
