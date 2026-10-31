using AlternAIs.Application;

namespace AlternAIs.Application.Tests;

public class AutoSendScriptTests
{
    [Fact]
    public void El_texto_va_como_cadena_JSON_escapada()
    {
        var js = AutoSendScript.Build("dijo \"hola\"\nchau");
        Assert.Contains("\"dijo \\\"hola\\\"\\nchau\"", js);
        Assert.DoesNotContain("__TEXT__", js);
    }

    [Fact]
    public void Enviar_se_puede_apagar()
    {
        Assert.EndsWith(",true)", AutoSendScript.Build("x", send: true));
        Assert.EndsWith(",false)", AutoSendScript.Build("x", send: false));
    }

    [Theory]
    [InlineData("\"ok\"", true)]
    [InlineData("\"none\"", false)]
    [InlineData(null, false)]
    public void Reconoce_el_resultado(string? result, bool expected) => Assert.Equal(expected, AutoSendScript.IsOk(result));
}
