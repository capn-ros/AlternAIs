using AlternAIs.Domain.Models;
using AlternAIs.Domain.UseCases;
using static AlternAIs.Domain.Tests.TestSupport;

namespace AlternAIs.Domain.Tests;

public class LimitUseCasesTests
{
    private const long H = 3_600_000L;
    private readonly FakeClock _clock = new(1_000_000L);

    [Fact]
    public void Marcar_limite_guarda_hasta_y_desde_ahora()
    {
        var r = new MarkLimit(_clock).Invoke(Acc("a"), 1_000_000L + 5 * H);
        Assert.Equal(1_000_000L + 5 * H, r.LimitUntil);
        Assert.Equal(1_000_000L, r.LimitStart);
    }

    [Fact]
    public void Marcar_un_limite_en_el_pasado_no_cambia_nada()
    {
        var a = Acc("a");
        Assert.Equal(a, new MarkLimit(_clock).Invoke(a, 999L));
    }

    [Fact]
    public void Quitar_la_marca_limpia_hasta_y_desde()
    {
        var r = Acc("a", 5_000_000L).WithoutLimit();
        Assert.Equal(0L, r.LimitUntil);
        Assert.Equal(0L, r.LimitStart);
    }

    [Fact]
    public void Vencer_limpia_solo_las_cuentas_vencidas_y_las_informa()
    {
        var list = new[] { Acc("a", 500L), Acc("b", 9_999_999L), Acc("c") };
        var r = new ExpireLimits(_clock).Invoke(list);
        Assert.Equal(new[] { "a" }, r.Expired.Select(a => a.Id));
        Assert.Equal(0L, r.Accounts[0].LimitUntil);
        Assert.Equal(9_999_999L, r.Accounts[1].LimitUntil);
    }

    [Fact]
    public void Si_no_vence_nada_devuelve_la_misma_lista()
    {
        var list = new[] { Acc("b", 9_999_999L) };
        var r = new ExpireLimits(_clock).Invoke(list);
        Assert.Equal(list, r.Accounts);
        Assert.Empty(r.Expired);
    }

    [Fact]
    public void Primera_disponible_ignora_la_actual_y_las_que_descansan()
    {
        var list = new[] { Acc("a"), Acc("b", 9_999_999L), Acc("c") };
        Assert.Equal("c", list.FirstAvailableExcept("a", _clock.NowMs)?.Id);
        Assert.Null(new[] { Acc("a"), Acc("b", 9_999_999L) }.FirstAvailableExcept("a", _clock.NowMs));
    }
}

public class LimitOptionsTests
{
    [Fact]
    public void Claude_ofrece_cuatro_opciones_con_la_hora_exacta_al_final()
    {
        var o = LimitOptions.ForService(AiService.Claude);
        Assert.Equal(
            new[] { "Dentro de 5 horas", "Dentro de 24 horas", "Dentro de 7 días", "A una hora exacta…" },
            o.Select(x => x.Label));
        Assert.IsType<ExactTimeOption>(o[^1]);
    }

    [Fact]
    public void Las_opciones_de_horas_suman_5_24_y_168_horas()
    {
        var hours = LimitOptions.ForService(AiService.Claude).OfType<AfterHours>().Select(x => x.Hours);
        Assert.Equal(new long[] { 5, 24, 168 }, hours);
    }

    [Fact] public void Dentro_de_N_horas_se_calcula_desde_ahora() =>
        Assert.Equal(1_000L + 5 * 3_600_000L, new AfterHours("x", 5).UntilMs(1_000L));
}

public class ValidateAccountFormTests
{
    private const int Color = 0x112233;

    private static AccountFormValid Valid(string name = "Trabajo", string purpose = "", string email = "") =>
        Assert.IsType<AccountFormValid>(ValidateAccountForm.Invoke(name, purpose, email, Color));

    [Fact] public void Un_nombre_vacio_pide_poner_un_nombre() =>
        Assert.Equal(new AccountFormInvalid("Poné un nombre"), ValidateAccountForm.Invoke("", "", "", Color));

    [Fact] public void Un_nombre_de_solo_espacios_tambien() =>
        Assert.Equal(new AccountFormInvalid("Poné un nombre"), ValidateAccountForm.Invoke("   ", "x", "a@b.com", Color));

    [Fact] public void El_nombre_se_recorta() => Assert.Equal("Estudio", Valid(name: "  Estudio ").Name);

    [Fact] public void El_nombre_se_corta_a_24_caracteres() =>
        Assert.Equal(new string('a', 24), Valid(name: new string('a', 40)).Name);

    [Fact]
    public void El_proposito_es_opcional_se_recorta_y_se_corta_a_60()
    {
        Assert.Equal("", Valid(purpose: "   ").Purpose);
        Assert.Equal("Clases", Valid(purpose: " Clases ").Purpose);
        Assert.Equal(new string('b', 60), Valid(purpose: new string('b', 100)).Purpose);
    }

    [Fact]
    public void Un_correo_valido_se_conserva_y_uno_invalido_queda_vacio()
    {
        Assert.Equal("yo@correo.com", Valid(email: " yo@correo.com ").Email);
        Assert.Equal("", Valid(email: "no es un correo").Email);
    }

    [Fact] public void El_color_pasa_tal_cual() => Assert.Equal(Color, Valid().Color);
}

public class AccountNavigationTests
{
    private static readonly string[] Ids = ["a", "b", "c"];

    [Fact]
    public void Siguiente_y_anterior()
    {
        Assert.Equal("b", AccountNavigation.Step(Ids, "a", +1));
        Assert.Equal("a", AccountNavigation.Step(Ids, "b", -1));
    }

    [Fact]
    public void Da_la_vuelta_en_los_extremos()
    {
        Assert.Equal("a", AccountNavigation.Step(Ids, "c", +1));
        Assert.Equal("c", AccountNavigation.Step(Ids, "a", -1));
    }

    [Fact]
    public void Con_menos_de_dos_cuentas_no_hay_a_donde_ir()
    {
        Assert.Null(AccountNavigation.Step([], null, +1));
        Assert.Null(AccountNavigation.Step(["a"], "a", +1));
    }

    [Fact]
    public void Si_la_actual_no_esta_en_la_lista_se_parte_desde_la_primera()
    {
        Assert.Equal("b", AccountNavigation.Step(Ids, "zzz", +1));
        Assert.Equal("b", AccountNavigation.Step(Ids, null, +1));
    }

    [Fact]
    public void Con_dos_cuentas_alterna()
    {
        Assert.Equal("b", AccountNavigation.Step(["a", "b"], "a", +1));
        Assert.Equal("a", AccountNavigation.Step(["a", "b"], "b", +1));
    }

    [Fact]
    public void El_sentido_de_la_animacion_sigue_el_orden_de_la_lista()
    {
        Assert.Equal(1, AccountNavigation.Direction(Ids, "a", "c"));
        Assert.Equal(-1, AccountNavigation.Direction(Ids, "c", "a"));
        Assert.Equal(0, AccountNavigation.Direction(Ids, "b", "b"));
    }

    [Fact]
    public void Sin_cuenta_anterior_no_hay_sentido()
    {
        Assert.Equal(0, AccountNavigation.Direction(Ids, null, "b"));
        Assert.Equal(0, AccountNavigation.Direction(Ids, "borrada", "b"));
    }
}

public class PlanImportTests
{
    private int _n;
    private readonly int[] _palette = [1, 2];
    private readonly long _now = 1_000L;

    private ImportPlanResult Run(IReadOnlyList<ImportedAccount> items, IReadOnlyList<Account>? existing = null, bool hasSelected = false) =>
        new PlanImport(() => "id" + (++_n), _palette).Invoke(AiService.Claude, existing ?? [], items, hasSelected, _now);

    [Fact]
    public void Agrega_cuentas_nuevas_con_id_propio()
    {
        var r = Run([new ImportedAccount("Trabajo", ColorHex: "#E07A5F")]);
        Assert.Equal(1, r.Added);
        Assert.Equal("id1", r.Accounts.Single().Id);
        Assert.Equal(unchecked((int)0xFFE07A5F), r.Accounts.Single().Color);
    }

    [Fact]
    public void Omite_nombres_repetidos_sin_importar_mayusculas_y_vacios()
    {
        var ex = new[] { new Account("x", "Trabajo", Color: 1) };
        var r = Run([new ImportedAccount("trabajo"), new ImportedAccount("   "), new ImportedAccount("Casa")], ex);
        Assert.Equal(1, r.Added);
        Assert.Equal(2, r.Skipped);
    }

    [Fact]
    public void Repetidos_dentro_del_mismo_archivo_tambien_se_omiten()
    {
        var r = Run([new ImportedAccount("A"), new ImportedAccount("a")]);
        Assert.Equal(1, r.Added);
        Assert.Equal(1, r.Skipped);
    }

    [Fact]
    public void Color_invalido_usa_la_paleta_segun_la_posicion()
    {
        var r = Run([new ImportedAccount("A", ColorHex: "rojo"), new ImportedAccount("B", ColorHex: "")]);
        Assert.Equal(_palette[0], r.Accounts[0].Color);
        Assert.Equal(_palette[1], r.Accounts[1].Color);
    }

    [Fact]
    public void Solo_conserva_limites_todavia_vigentes()
    {
        var r = Run([
            new ImportedAccount("Vigente", LimitUntil: 5_000L, LimitStart: 100L),
            new ImportedAccount("Vencido", LimitUntil: 500L, LimitStart: 100L)
        ]);
        Assert.Equal(5_000L, r.Accounts[0].LimitUntil);
        Assert.Equal(0L, r.Accounts[1].LimitUntil);
        Assert.Equal(0L, r.Accounts[1].LimitStart);
    }

    [Fact]
    public void Corta_nombre_a_24_y_proposito_a_60()
    {
        var r = Run([new ImportedAccount(new string('n', 40), Purpose: new string('p', 100))]);
        Assert.Equal(24, r.Accounts[0].Name.Length);
        Assert.Equal(60, r.Accounts[0].Purpose.Length);
    }

    [Fact]
    public void Maximo_50_por_servicio() =>
        Assert.Equal(50, Run(Enumerable.Range(1, 80).Select(i => new ImportedAccount("c" + i)).ToList()).Added);

    [Fact]
    public void Sanea_url_y_correo()
    {
        var r = Run([new ImportedAccount("A", LastUrl: "https://evil.com/x", Email: "mal formado")]);
        Assert.Equal("", r.Accounts[0].LastUrl);
        Assert.Equal("", r.Accounts[0].Email);
    }

    [Fact]
    public void Sin_cuenta_activa_y_lista_vacia_queda_activa_la_primera_importada() =>
        Assert.Equal("id1", Run([new ImportedAccount("A"), new ImportedAccount("B", Selected: true)]).NewSelectedId);

    [Fact]
    public void Sin_cuenta_activa_pero_con_cuentas_previas_queda_activa_la_marcada_en_el_archivo()
    {
        var ex = new[] { new Account("x", "Previa", Color: 1) };
        Assert.Equal("id2", Run([new ImportedAccount("A"), new ImportedAccount("B", Selected: true)], ex).NewSelectedId);
    }

    [Fact] public void Si_ya_habia_cuenta_activa_no_se_cambia() =>
        Assert.Null(Run([new ImportedAccount("A", Selected: true)], hasSelected: true).NewSelectedId);
}
