using AlternAIs.Domain.Models;

namespace AlternAIs.Domain.Tests;

public class AccountTests
{
    private const long H = 3_600_000L;
    private const long Now = 1_000_000_000_000L;

    private static Account Acc(long until = 0, long start = 0) =>
        new("a1", "Trabajo", Color: unchecked((int)0xFF112233), LimitUntil: until, LimitStart: start);

    [Fact] public void ProfileName_usa_el_id() => Assert.Equal("acc_a1", Acc().ProfileName);

    [Fact]
    public void Sin_limite_no_esta_en_descanso_y_la_fraccion_es_0()
    {
        Assert.False(Acc().IsResting(Now));
        Assert.Equal(0f, Acc().LimitFraction(Now));
    }

    [Fact]
    public void Limite_vencido_deja_la_cuenta_disponible()
    {
        var a = Acc(until: Now - 1, start: Now - H);
        Assert.False(a.IsResting(Now));
        Assert.Equal(0f, a.LimitFraction(Now));
    }

    [Fact] public void Recien_marcado_la_fraccion_es_1() =>
        Assert.Equal(1f, Acc(until: Now + 5 * H, start: Now).LimitFraction(Now), 0.0001f);

    [Fact] public void A_mitad_del_descanso_la_fraccion_es_0_5() =>
        Assert.Equal(0.5f, Acc(until: Now + 5 * H, start: Now - 5 * H).LimitFraction(Now), 0.0001f);

    [Fact]
    public void Sin_inicio_guardado_se_supone_el_plazo_estandar_mas_cercano()
    {
        // faltan 2,5 h y no hay inicio: se asume 5 h -> 0,5
        Assert.Equal(0.5f, Acc(until: Now + (long)(2.5 * H)).LimitFraction(Now), 0.0001f);
        // faltan 12 h: se asume 24 h -> 0,5
        Assert.Equal(0.5f, Acc(until: Now + 12 * H).LimitFraction(Now), 0.0001f);
    }

    [Fact] public void Plazo_mayor_que_30_dias_usa_lo_que_falta_y_da_1() =>
        Assert.Equal(1f, Acc(until: Now + 40 * 24 * H).LimitFraction(Now), 0.0001f);

    [Fact]
    public void Inicio_invalido_posterior_al_final_se_ignora()
    {
        var f = Acc(until: Now + 2 * H, start: Now + 10 * H).LimitFraction(Now);
        Assert.InRange(f, 0f, 1f);
    }

    [Fact]
    public void NewId_son_12_caracteres_hexadecimales_y_no_se_repiten()
    {
        var a = Account.NewId();
        var b = Account.NewId();
        Assert.Matches("^[0-9a-f]{12}$", a);
        Assert.NotEqual(a, b);
    }
}

public class AiServiceTests
{
    [Fact]
    public void FromKey_desconocido_cae_en_Claude()
    {
        Assert.Equal(AiService.Claude, AiServices.FromKey(null));
        Assert.Equal(AiService.Claude, AiServices.FromKey("otro"));
        Assert.Equal(AiService.Claude, AiServices.FromKey("replit"));
    }

    [Fact]
    public void OwnsHost_acepta_el_dominio_y_sus_subdominios_pero_no_parecidos()
    {
        Assert.True(AiService.Claude.OwnsHost("claude.ai"));
        Assert.True(AiService.Claude.OwnsHost("api.claude.ai"));
        Assert.False(AiService.Claude.OwnsHost("fakeclaude.ai"));
        Assert.False(AiService.Claude.OwnsHost("replit.com"));
    }

    [Fact]
    public void Cada_servicio_tiene_su_clave_nombre_y_sitio()
    {
        Assert.Equal("claude", AiService.Claude.Key());
        Assert.Equal("Claude", AiService.Claude.Label());
        Assert.Equal("https://claude.ai/", AiService.Claude.StartUrl());
    }
}

public class AccountPaletteTests
{
    [Fact]
    public void Hay_ocho_colores_distintos_y_opacos()
    {
        Assert.Equal(8, AccountPalette.Colors.Count);
        Assert.Equal(8, AccountPalette.Colors.Distinct().Count());
        Assert.All(AccountPalette.Colors, c => Assert.Equal(0xFF, (c >> 24) & 0xFF));
    }

    [Fact] public void El_primero_es_el_coral_de_siempre() =>
        Assert.Equal(unchecked((int)0xFFE07A5F), AccountPalette.Colors[0]);

    [Fact]
    public void El_color_sugerido_reparte_en_orden_y_da_la_vuelta()
    {
        Assert.Equal(AccountPalette.Colors[0], AccountPalette.DefaultFor(0));
        Assert.Equal(AccountPalette.Colors[3], AccountPalette.DefaultFor(3));
        Assert.Equal(AccountPalette.Colors[0], AccountPalette.DefaultFor(8));
        Assert.Equal(AccountPalette.Colors[1], AccountPalette.DefaultFor(9));
        Assert.Equal(AccountPalette.Colors[0], AccountPalette.DefaultFor(-3));
    }

    [Fact]
    public void El_degrade_del_boton_mas_cierra_el_circulo()
    {
        var sweep = AccountPalette.SweepColors();
        Assert.Equal(9, sweep.Count);
        Assert.Equal(AccountPalette.Colors, sweep.Take(8));
        Assert.Equal(sweep[0], sweep[8]);
    }
}

public class ColorHexTests
{
    [Fact] public void RGB_de_6_digitos_queda_opaco() =>
        Assert.Equal(unchecked((int)0xFFE07A5F), ColorHex.Parse("#E07A5F"));

    [Fact] public void ARGB_de_8_digitos_se_respeta() =>
        Assert.Equal(unchecked((int)0x80112233), ColorHex.Parse("#80112233"));

    [Fact]
    public void Lo_que_no_es_un_color_da_nulo()
    {
        Assert.Null(ColorHex.Parse("rojo"));
        Assert.Null(ColorHex.Parse(""));
        Assert.Null(ColorHex.Parse(null));
        Assert.Null(ColorHex.Parse("#12345"));
        Assert.Null(ColorHex.Parse("#E07A5F\n"));
    }

    [Fact] public void ToRgb_escribe_seis_digitos_en_mayuscula_sin_transparencia()
    {
        Assert.Equal("#E07A5F", ColorHex.ToRgb(unchecked((int)0xFFE07A5F)));
        Assert.Equal("#00000A", ColorHex.ToRgb(unchecked((int)0x8000000A)));
    }
}

public class SnippetsTests
{
    private static readonly IReadOnlyList<string> Base = ["a", "b", "c"];

    [Fact] public void Hay_cuatro_mensajes_de_ejemplo() => Assert.Equal(4, Snippets.Defaults.Count);

    [Fact]
    public void Normalize_saca_vacios_limita_cantidad_y_largo()
    {
        Assert.Equal(new[] { "a", "b" }, Snippets.Normalize(["a", "  ", "", "b"]));
        Assert.Equal(Snippets.MaxCount, Snippets.Normalize(Enumerable.Range(0, 80).Select(i => "m" + i)).Count);
        Assert.Equal(Snippets.MaxLength, Snippets.Normalize([new string('x', 5000)])[0].Length);
    }

    [Fact]
    public void Add_agrega_al_final_y_ignora_el_texto_en_blanco()
    {
        Assert.Equal(new[] { "a", "b", "c", "d" }, Snippets.Add(Base, "d"));
        Assert.Equal(Base, Snippets.Add(Base, "   "));
    }

    [Fact]
    public void Replace_cambia_uno_y_ignora_indices_o_textos_invalidos()
    {
        Assert.Equal(new[] { "a", "X", "c" }, Snippets.Replace(Base, 1, "X"));
        Assert.Equal(Base, Snippets.Replace(Base, 5, "X"));
        Assert.Equal(Base, Snippets.Replace(Base, -1, "X"));
        Assert.Equal(Base, Snippets.Replace(Base, 1, " "));
    }

    [Fact]
    public void MoveUp_sube_una_posicion_y_el_primero_no_se_mueve()
    {
        Assert.Equal(new[] { "a", "c", "b" }, Snippets.MoveUp(Base, 2));
        Assert.Equal(Base, Snippets.MoveUp(Base, 0));
        Assert.Equal(Base, Snippets.MoveUp(Base, 9));
    }

    [Fact]
    public void Remove_borra_uno_y_ignora_indices_invalidos()
    {
        Assert.Equal(new[] { "a", "c" }, Snippets.Remove(Base, 1));
        Assert.Equal(Base, Snippets.Remove(Base, 3));
        Assert.Equal(Base, Snippets.Remove(Base, -1));
    }

    [Fact]
    public void Las_operaciones_no_modifican_la_lista_original()
    {
        _ = Snippets.Add(Base, "d");
        _ = Snippets.Remove(Base, 0);
        Assert.Equal(new[] { "a", "b", "c" }, Base);
    }
}
