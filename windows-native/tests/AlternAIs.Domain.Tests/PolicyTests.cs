using AlternAIs.Domain.Models;
using AlternAIs.Domain.Policies;

namespace AlternAIs.Domain.Tests;

public class EmailPolicyTests
{
    [Fact] public void Acepta_un_correo_simple_y_lo_recorta() => Assert.Equal("a@b.com", EmailPolicy.Clean("  a@b.com "));
    [Fact] public void Rechaza_sin_arroba() => Assert.Equal("", EmailPolicy.Clean("abc"));
    [Fact] public void Rechaza_espacios_en_el_medio() => Assert.Equal("", EmailPolicy.Clean("a b@c.com"));
    [Fact] public void Rechaza_dos_arrobas() => Assert.Equal("", EmailPolicy.Clean("a@b@c"));
    [Fact] public void Nulo_da_vacio() => Assert.Equal("", EmailPolicy.Clean(null));

    [Fact]
    public void Corta_a_80_caracteres_y_por_eso_queda_invalido_si_pierde_la_arroba() =>
        Assert.Equal("", EmailPolicy.Clean(new string('x', 100) + "@a.com"));
}

public class AccountInitialTests
{
    [Fact]
    public void Primera_letra_en_mayuscula()
    {
        Assert.Equal("T", AccountInitial.Of("Trabajo"));
        Assert.Equal("C", AccountInitial.Of("  casa "));
    }

    [Fact]
    public void Salta_simbolos_hasta_encontrar_una_letra_o_cifra()
    {
        Assert.Equal("A", AccountInitial.Of("--a"));
        Assert.Equal("1", AccountInitial.Of("123"));
    }

    [Fact]
    public void Respeta_las_letras_con_acento_y_la_enie()
    {
        Assert.Equal("Ñ", AccountInitial.Of("ñandú"));
        Assert.Equal("É", AccountInitial.Of("épica"));
    }

    [Fact]
    public void Sin_letras_ni_cifras_queda_un_punto()
    {
        Assert.Equal("•", AccountInitial.Of(""));
        Assert.Equal("•", AccountInitial.Of("   "));
        Assert.Equal("•", AccountInitial.Of("!!!"));
    }
}

public class UrlRestorePolicyTests
{
    private static string? Claude(string? url) => UrlRestorePolicy.Restorable(AiService.Claude, url);

    [Fact] public void Acepta_https_del_sitio_y_saca_query_y_fragmento() =>
        Assert.Equal("https://claude.ai/chat/abc", Claude("https://claude.ai/chat/abc?x=1#frag"));

    [Fact] public void Acepta_subdominios_del_servicio() =>
        Assert.Equal("https://www.claude.ai/new", Claude("https://www.claude.ai/new"));

    [Fact] public void Acepta_el_puerto_443_explicito() =>
        Assert.Equal("https://claude.ai:443/new", Claude("https://claude.ai:443/new"));

    [Fact] public void Una_url_sin_ruta_queda_sin_ruta() =>
        Assert.Equal("https://claude.ai", Claude("https://claude.ai?x=1"));

    [Fact] public void Rechaza_http() => Assert.Null(Claude("http://claude.ai/chat"));
    [Fact] public void Rechaza_otro_dominio() => Assert.Null(Claude("https://evil.com/chat"));
    [Fact] public void Rechaza_dominio_que_solo_termina_parecido() => Assert.Null(Claude("https://notclaude.ai/chat"));
    [Fact] public void Rechaza_credenciales_en_la_url() => Assert.Null(Claude("https://user:pw@claude.ai/chat"));
    [Fact] public void Rechaza_puertos_distintos_de_443() => Assert.Null(Claude("https://claude.ai:8443/chat"));

    [Fact]
    public void Rechaza_esquemas_peligrosos()
    {
        Assert.Null(Claude("javascript:alert(1)"));
        Assert.Null(Claude("file:///sdcard/x"));
    }

    [Fact]
    public void Rechaza_vacio_nulo_y_demasiado_largo()
    {
        Assert.Null(Claude(null));
        Assert.Null(Claude("   "));
        Assert.Null(Claude("https://claude.ai/" + new string('a', 5000)));
    }

    [Fact] public void Rechaza_texto_que_no_es_url() => Assert.Null(Claude("no es una url ::"));

    [Fact]
    public void Una_url_de_claude_no_sirve_para_replit()
    {
        Assert.Null(UrlRestorePolicy.Restorable(AiService.Replit, "https://claude.ai/chat"));
        Assert.Equal("https://replit.com/@u/p", UrlRestorePolicy.Restorable(AiService.Replit, "https://replit.com/@u/p"));
    }
}
