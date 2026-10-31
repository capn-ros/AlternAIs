using AlternAIs.Domain.Models;

namespace AlternAIs.Domain.Policies;

/// <summary>
/// Solo se restaura una URL HTTPS del sitio del servicio, sin query ni fragmento, sin credenciales
/// y sin puertos raros. Nunca un destino externo guardado en el estado.
/// </summary>
public static class UrlRestorePolicy
{
    private const int MaxLength = 4096;

    /// <summary>La URL limpia para restaurar, o null si no es segura.</summary>
    public static string? Restorable(AiService service, string? raw)
    {
        if (string.IsNullOrWhiteSpace(raw) || raw.Length > MaxLength) return null;
        // Una URL válida no lleva espacios ni saltos de línea (la de Android tampoco los acepta).
        if (raw.Any(char.IsWhiteSpace)) return null;
        if (!Uri.TryCreate(raw, UriKind.Absolute, out var uri)) return null;
        if (!string.Equals(uri.Scheme, "https", StringComparison.OrdinalIgnoreCase)) return null;
        if (uri.Host.Length == 0 || !service.OwnsHost(uri.Host.ToLowerInvariant())) return null;
        if (uri.UserInfo.Length > 0) return null;
        if (uri.Port != 443) return null;

        // Se arma desde el texto original para conservar tal cual el puerto explícito y la ruta.
        var afterScheme = raw[(raw.IndexOf("://", StringComparison.Ordinal) + 3)..];
        var end = afterScheme.IndexOfAny(['/', '?', '#']);
        var authority = end < 0 ? afterScheme : afterScheme[..end];
        if (authority.Contains('@')) return null;
        var rest = end < 0 ? "" : afterScheme[end..];
        var cut = rest.IndexOfAny(['?', '#']);
        var path = cut < 0 ? rest : rest[..cut];
        return "https://" + authority + path;
    }
}
