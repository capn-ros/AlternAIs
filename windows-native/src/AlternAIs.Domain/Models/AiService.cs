namespace AlternAIs.Domain.Models;

/// <summary>
/// Cada servicio es una «mitad» de la app: sitio propio y cuentas propias. Claude es la principal (va primero y es
/// la que se abre siempre); las demás se eligen desde Ajustes. Dominio puro: no conoce colores ni temas.
/// </summary>
public enum AiService { Claude, ChatGpt, Gemini, Grok, DeepSeek, Replit }

public static class AiServices
{
    public static string Key(this AiService s) => s switch
    {
        AiService.Claude => "claude",
        AiService.ChatGpt => "chatgpt",
        AiService.Gemini => "gemini",
        AiService.Grok => "grok",
        AiService.DeepSeek => "deepseek",
        AiService.Replit => "replit",
        _ => throw new ArgumentOutOfRangeException(nameof(s))
    };

    public static string Label(this AiService s) => s switch
    {
        AiService.Claude => "Claude",
        AiService.ChatGpt => "ChatGPT",
        AiService.Gemini => "Gemini",
        AiService.Grok => "Grok",
        AiService.DeepSeek => "DeepSeek",
        AiService.Replit => "Replit",
        _ => throw new ArgumentOutOfRangeException(nameof(s))
    };

    public static string StartUrl(this AiService s) => s switch
    {
        AiService.Claude => "https://claude.ai/",
        AiService.ChatGpt => "https://chatgpt.com/",
        AiService.Gemini => "https://gemini.google.com/",
        AiService.Grok => "https://grok.com/",
        AiService.DeepSeek => "https://chat.deepseek.com/",
        AiService.Replit => "https://replit.com/",
        _ => throw new ArgumentOutOfRangeException(nameof(s))
    };

    public static IReadOnlyList<string> Hosts(this AiService s) => s switch
    {
        AiService.Claude => new[] { "claude.ai" },
        AiService.ChatGpt => new[] { "chatgpt.com", "openai.com" },
        AiService.Gemini => new[] { "gemini.google.com" },
        AiService.Grok => new[] { "grok.com", "x.ai" },
        AiService.DeepSeek => new[] { "deepseek.com" },
        AiService.Replit => new[] { "replit.com", "replit.dev", "repl.co" },
        _ => throw new ArgumentOutOfRangeException(nameof(s))
    };

    /// <summary>Tema con el que arranca cada servicio: oscuro, salvo Replit (claro).</summary>
    public static string DefaultTheme(this AiService s) => s == AiService.Replit ? "light" : "dark";

    /// <summary>El dominio del servicio o uno de sus subdominios (no sirve uno que solo termina parecido).</summary>
    public static bool OwnsHost(this AiService s, string host) =>
        s.Hosts().Any(h => host == h || host.EndsWith("." + h, StringComparison.Ordinal));

    /// <summary>Clave desconocida o nula = Claude.</summary>
    public static AiService FromKey(string? key) =>
        Enum.GetValues<AiService>().FirstOrDefault(s => s.Key() == key);
}
