namespace AlternAIs.Domain.Models;

/// <summary>
/// Cada servicio es una «mitad» de la app: sitio propio y cuentas propias. Dominio puro: no conoce
/// colores ni temas (eso es de la capa de interfaz).
/// </summary>
public enum AiService { Claude, Replit }

public static class AiServices
{
    public static string Key(this AiService s) => s switch
    {
        AiService.Claude => "claude",
        AiService.Replit => "replit",
        _ => throw new ArgumentOutOfRangeException(nameof(s))
    };

    public static string Label(this AiService s) => s switch
    {
        AiService.Claude => "Claude",
        AiService.Replit => "Replit",
        _ => throw new ArgumentOutOfRangeException(nameof(s))
    };

    public static string StartUrl(this AiService s) => s switch
    {
        AiService.Claude => "https://claude.ai/",
        AiService.Replit => "https://replit.com/",
        _ => throw new ArgumentOutOfRangeException(nameof(s))
    };

    public static IReadOnlyList<string> Hosts(this AiService s) => s switch
    {
        AiService.Claude => new[] { "claude.ai" },
        AiService.Replit => new[] { "replit.com" },
        _ => throw new ArgumentOutOfRangeException(nameof(s))
    };

    /// <summary>El dominio del servicio o uno de sus subdominios (no sirve uno que solo termina parecido).</summary>
    public static bool OwnsHost(this AiService s, string host) =>
        s.Hosts().Any(h => host == h || host.EndsWith("." + h, StringComparison.Ordinal));

    /// <summary>Clave desconocida o nula = Claude.</summary>
    public static AiService FromKey(string? key) =>
        Enum.GetValues<AiService>().FirstOrDefault(s => s.Key() == key);
}
