using AlternAIs.Domain.UseCases;

namespace AlternAIs.Domain.Tests;

public class BrowserAndSendTests
{
    private static BrowserInput R(string s) => BrowserRules.Resolve(s, x => x.Replace(" ", "%20"));

    [Fact] public void Vacio_no_hace_nada() => Assert.IsType<BrowserIgnore>(R("  "));

    [Fact]
    public void Http_y_https_se_cargan_tal_cual()
    {
        Assert.Equal(new BrowserLoad("https://a.com/x"), R(" https://a.com/x "));
        Assert.Equal(new BrowserLoad("HTTP://A.COM"), R("HTTP://A.COM"));
    }

    [Fact] public void Otros_esquemas_se_rechazan() => Assert.Equal(new BrowserReject(BrowserRules.OnlyHttpMessage), R("file:///c:/x"));

    [Fact] public void Dominio_sin_esquema_pasa_a_https() => Assert.Equal(new BrowserLoad("https://github.com/x"), R("github.com/x"));

    [Fact]
    public void Texto_suelto_o_con_espacios_se_busca()
    {
        Assert.Equal(new BrowserLoad("https://duckduckgo.com/?q=kotlin"), R("kotlin"));
        Assert.Equal(new BrowserLoad("https://duckduckgo.com/?q=hola%20mundo.com"), R("hola mundo.com"));
    }

    [Fact]
    public void Solo_se_recuerdan_paginas_web_y_la_primera_vez_es_GitHub()
    {
        Assert.True(BrowserRules.IsRememberable("https://a.com"));
        Assert.False(BrowserRules.IsRememberable("about:blank"));
        Assert.False(BrowserRules.IsRememberable(null));
        Assert.Equal("https://github.com/", BrowserRules.StartUrl(null));
        Assert.Equal("https://x.com/", BrowserRules.StartUrl("https://x.com/"));
    }

    [Fact]
    public void El_envio_reintenta_hasta_22_veces()
    {
        Assert.IsType<SendDone>(AutoSendPolicy.Next(0, true));
        Assert.Equal(new SendRetry(2000), AutoSendPolicy.Next(21, false));
        Assert.IsType<SendGiveUp>(AutoSendPolicy.Next(22, false));
    }
}
