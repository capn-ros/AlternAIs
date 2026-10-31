using AlternAIs.Application;
using AlternAIs.Domain.Models;
using AlternAIs.Domain.UseCases;

namespace AlternAIs.Application.Tests;

public class AccountsServiceTests
{
    private readonly FakeClock _clock = new(T.Now);
    private readonly IdSource _ids = new();

    private (AccountsService Svc, MemoryStore Store) Make(AppState? initial = null)
    {
        var store = new MemoryStore(initial);
        return (new AccountsService(store, _clock, _ids.Next), store);
    }

    private static AccountFormValid Form(string name = "Trabajo") => new(name, "p", "a@b.com", 7);

    private static AppState WithClaude(params Account[] accounts) =>
        AppState.Initial.WithBucket(AiService.Claude, new ServiceBucket(accounts, null));

    [Fact]
    public void Agregar_crea_la_cuenta_y_la_deja_activa_y_guarda()
    {
        var (svc, store) = Make();
        var a = svc.Add(Form());
        Assert.NotNull(a);
        Assert.Equal("000000000001", a!.Id);
        Assert.Equal(a.Id, svc.SelectedId);
        Assert.Equal("Trabajo", svc.Selected!.Name);
        Assert.Equal(1, store.Saves);
    }

    [Fact]
    public void Agregar_avisa_al_evento_Changed()
    {
        var (svc, _) = Make();
        var calls = 0;
        svc.Changed += _ => calls++;
        svc.Add(Form());
        Assert.Equal(1, calls);
    }

    [Fact]
    public void No_se_pueden_agregar_mas_de_50()
    {
        var many = Enumerable.Range(0, 50).Select(i => T.Acc($"{i + 1:x12}")).ToArray();
        var (svc, store) = Make(WithClaude(many));
        Assert.Null(svc.Add(Form()));
        Assert.Equal(0, store.Saves);
    }

    [Fact]
    public void Editar_conserva_el_id_y_cambia_los_datos()
    {
        var (svc, _) = Make(WithClaude(T.Acc("aaaaaaaaaaaa")));
        svc.Edit("aaaaaaaaaaaa", new AccountFormValid("Nuevo", "otro", "x@y.com", 9));
        var a = svc.Accounts.Single();
        Assert.Equal(("aaaaaaaaaaaa", "Nuevo", "otro", "x@y.com", 9), (a.Id, a.Name, a.Purpose, a.Email, a.Color));
    }

    [Fact]
    public void Editar_una_cuenta_que_no_existe_no_guarda()
    {
        var (svc, store) = Make(WithClaude(T.Acc("aaaaaaaaaaaa")));
        svc.Edit("zzzz", Form());
        Assert.Equal(0, store.Saves);
    }

    [Fact]
    public void Quitar_la_activa_deja_la_primera()
    {
        var state = AppState.Initial.WithBucket(AiService.Claude,
            new ServiceBucket([T.Acc("aaaaaaaaaaaa"), T.Acc("bbbbbbbbbbbb")], "bbbbbbbbbbbb"));
        var (svc, _) = Make(state);
        svc.Remove("bbbbbbbbbbbb");
        Assert.Equal("aaaaaaaaaaaa", svc.SelectedId);
        Assert.Single(svc.Accounts);
    }

    [Fact]
    public void Quitar_la_ultima_deja_sin_cuenta_activa()
    {
        var (svc, _) = Make(WithClaude(T.Acc("aaaaaaaaaaaa")));
        svc.Remove("aaaaaaaaaaaa");
        Assert.Null(svc.SelectedId);
        Assert.Empty(svc.Accounts);
    }

    [Fact]
    public void Elegir_cambia_la_activa_y_ignora_ids_desconocidos()
    {
        var (svc, store) = Make(WithClaude(T.Acc("aaaaaaaaaaaa"), T.Acc("bbbbbbbbbbbb")));
        svc.Select("bbbbbbbbbbbb");
        Assert.Equal("bbbbbbbbbbbb", svc.SelectedId);
        var saves = store.Saves;
        svc.Select("nada");
        svc.Select("bbbbbbbbbbbb");
        Assert.Equal(saves, store.Saves);
    }

    [Fact]
    public void Step_da_la_vuelta_y_con_una_sola_cuenta_no_hace_nada()
    {
        var (svc, _) = Make(AppState.Initial.WithBucket(AiService.Claude,
            new ServiceBucket([T.Acc("aaaaaaaaaaaa"), T.Acc("bbbbbbbbbbbb")], "bbbbbbbbbbbb")));
        Assert.Equal("aaaaaaaaaaaa", svc.Step(+1));
        Assert.Equal("bbbbbbbbbbbb", svc.Step(-1));

        var (solo, _) = Make(WithClaude(T.Acc("aaaaaaaaaaaa")));
        Assert.Null(solo.Step(+1));
    }

    [Fact]
    public void Marcar_limite_guarda_inicio_y_fin_y_en_el_pasado_no_hace_nada()
    {
        var (svc, store) = Make(WithClaude(T.Acc("aaaaaaaaaaaa")));
        svc.MarkLimit("aaaaaaaaaaaa", T.Now + 5 * T.Hour);
        var a = svc.Accounts.Single();
        Assert.Equal(T.Now + 5 * T.Hour, a.LimitUntil);
        Assert.Equal(T.Now, a.LimitStart);
        var saves = store.Saves;
        svc.MarkLimit("aaaaaaaaaaaa", T.Now - 1);
        Assert.Equal(saves, store.Saves);
    }

    [Fact]
    public void Quitar_la_marca_de_limite()
    {
        var (svc, _) = Make(WithClaude(T.Acc("aaaaaaaaaaaa", until: T.Now + T.Hour)));
        svc.ClearLimit("aaaaaaaaaaaa");
        Assert.Equal(0, svc.Accounts.Single().LimitUntil);
    }

    [Fact]
    public void Vencer_limites_limpia_ambos_servicios_y_devuelve_las_cuentas()
    {
        var state = AppState.Initial
            .WithBucket(AiService.Claude, new ServiceBucket([T.Acc("aaaaaaaaaaaa", until: T.Now - 1)], null))
            .WithBucket(AiService.Replit, new ServiceBucket([T.Acc("bbbbbbbbbbbb", until: T.Now + T.Hour)], null));
        var (svc, store) = Make(state);
        var expired = svc.ExpireLimits();
        Assert.Equal(new[] { "aaaaaaaaaaaa" }, expired.Select(a => a.Id).ToArray());
        Assert.Equal(0, svc.State.Claude.Accounts[0].LimitUntil);
        Assert.True(svc.State.Replit.Accounts[0].LimitUntil > 0);
        Assert.Equal(1, store.Saves);
    }

    [Fact]
    public void Vencer_limites_sin_nada_vencido_no_guarda()
    {
        var (svc, store) = Make(WithClaude(T.Acc("aaaaaaaaaaaa", until: T.Now + T.Hour)));
        Assert.Empty(svc.ExpireLimits());
        Assert.Equal(0, store.Saves);
    }

    [Fact]
    public void Primera_cuenta_libre_distinta_de_la_actual()
    {
        var state = AppState.Initial.WithBucket(AiService.Claude, new ServiceBucket(
            [T.Acc("aaaaaaaaaaaa"), T.Acc("bbbbbbbbbbbb", until: T.Now + T.Hour), T.Acc("cccccccccccc")], "aaaaaaaaaaaa"));
        var (svc, _) = Make(state);
        Assert.Equal("cccccccccccc", svc.FirstAvailableExceptCurrent()!.Id);
    }

    [Fact]
    public void Cambiar_de_servicio_usa_las_cuentas_de_ese_servicio()
    {
        var state = AppState.Initial.WithBucket(AiService.Replit, new ServiceBucket([T.Acc("rrrrrrrrrrrr")], null));
        var (svc, _) = Make(state);
        Assert.Empty(svc.Accounts);
        svc.SwitchService(AiService.Replit);
        Assert.Equal("rrrrrrrrrrrr", svc.SelectedId);
        Assert.Single(svc.Accounts);
    }

    [Fact]
    public void Agregar_en_Replit_no_toca_Claude()
    {
        var (svc, _) = Make(WithClaude(T.Acc("aaaaaaaaaaaa")));
        svc.SwitchService(AiService.Replit);
        svc.Add(Form("R"));
        Assert.Single(svc.State.Claude.Accounts);
        Assert.Single(svc.State.Replit.Accounts);
    }

    [Fact]
    public void Recordar_url_solo_guarda_direcciones_seguras_del_servicio()
    {
        var (svc, store) = Make(WithClaude(T.Acc("aaaaaaaaaaaa")));
        svc.RememberUrl("aaaaaaaaaaaa", "https://claude.ai/chat/1?x=2");
        Assert.Equal("https://claude.ai/chat/1", svc.Accounts[0].LastUrl);
        var saves = store.Saves;
        svc.RememberUrl("aaaaaaaaaaaa", "https://malo.com/x");
        Assert.Equal(saves, store.Saves);
    }

    [Fact]
    public void Mensaje_automatico_se_recorta_y_se_guarda()
    {
        var (svc, _) = Make(WithClaude(T.Acc("aaaaaaaaaaaa")));
        svc.SetAutoReply("aaaaaaaaaaaa", "  hola  ");
        Assert.Equal("hola", svc.Accounts[0].AutoReply);
        svc.SetAutoReply("aaaaaaaaaaaa", new string('x', 5000));
        Assert.Equal(4000, svc.Accounts[0].AutoReply.Length);
    }

    [Fact]
    public void Importar_agrega_omite_repetidas_y_no_trae_ids_ni_sesiones()
    {
        var (svc, _) = Make(WithClaude(T.Acc("aaaaaaaaaaaa", name: "Trabajo")));
        var incoming = new Dictionary<AiService, IReadOnlyList<ImportedAccount>>
        {
            [AiService.Claude] = [new ImportedAccount("trabajo"), new ImportedAccount("Estudio", ColorHex: "#112233")],
            [AiService.Replit] = [new ImportedAccount("Rep", Selected: true)]
        };
        var r = svc.Import(incoming);
        Assert.Equal(new ImportSummary(2, 1), r);
        Assert.Equal(2, svc.State.Claude.Accounts.Count);
        Assert.Equal("Rep", svc.State.Replit.Accounts.Single().Name);
        Assert.Equal(svc.State.Replit.Accounts[0].Id, svc.State.Replit.SelectedId);
        Assert.Equal("aaaaaaaaaaaa", svc.State.Claude.SelectedId ?? "aaaaaaaaaaaa");
    }

    [Fact]
    public void Importar_sin_nada_nuevo_no_guarda()
    {
        var (svc, store) = Make(WithClaude(T.Acc("aaaaaaaaaaaa", name: "Trabajo")));
        svc.Import(new Dictionary<AiService, IReadOnlyList<ImportedAccount>> { [AiService.Claude] = [new ImportedAccount("trabajo")] });
        Assert.Equal(0, store.Saves);
    }

    [Fact]
    public void Tema_tutorial_y_ajustes_se_guardan()
    {
        var (svc, _) = Make();
        svc.SetTheme(AiService.Claude, "light");
        svc.SetTheme(AiService.Replit, "raro");
        svc.MarkTutorialShown();
        svc.SetSettings(new AppSettings("bottom", true, true));
        svc.SetBrowser(new BrowserSettings("https://a.com", "ask", @"C:\x"));
        Assert.Equal(("light", "dark", true), (svc.State.ClaudeTheme, svc.State.ReplitTheme, svc.State.TutorialShown));
        Assert.Equal("bottom", svc.State.Settings.RailPos);
        Assert.Equal("ask", svc.State.Browser.DlMode);
    }

    [Fact]
    public void Mensajes_guardados_se_normalizan()
    {
        var (svc, _) = Make();
        svc.SetSnippets(["  ", "uno", "dos"]);
        Assert.Equal(new[] { "uno", "dos" }, svc.State.Snippets.ToArray());
    }

    [Fact]
    public void Una_cuenta_del_otro_servicio_se_actualiza_sin_cambiar_de_servicio()
    {
        var state = AppState.Initial.WithBucket(AiService.Replit, new ServiceBucket([T.Acc("rrrrrrrrrrrr")], null));
        var (svc, _) = Make(state);
        Assert.Equal(AiService.Claude, svc.State.Service);
        Assert.Equal(AiService.Replit, svc.ServiceOf("rrrrrrrrrrrr"));
        svc.RememberUrl("rrrrrrrrrrrr", "https://replit.com/@u/p");
        Assert.Equal("https://replit.com/@u/p", svc.State.Replit.Accounts[0].LastUrl);
        svc.SetAutoReply("rrrrrrrrrrrr", "hola");
        Assert.Equal("hola", svc.State.Replit.Accounts[0].AutoReply);
        Assert.Null(svc.ServiceOf("nada"));
    }
}
