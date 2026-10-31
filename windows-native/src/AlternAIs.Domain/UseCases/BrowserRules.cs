namespace AlternAIs.Domain.UseCases;

/// <summary>Qué hacer con lo escrito en la barra de direcciones del navegador interno.</summary>
public abstract record BrowserInput;
public sealed record BrowserIgnore : BrowserInput;
public sealed record BrowserLoad(string Url) : BrowserInput;
public sealed record BrowserReject(string Message) : BrowserInput;

/// <summary>Mismas reglas que el navegador de Android.</summary>
public static class BrowserRules
{
    public const string OnlyHttpMessage = "Solo se pueden abrir direcciones http y https";
    public const string SearchUrl = "https://duckduckgo.com/?q=";

    public static readonly IReadOnlyList<(string Label, string Url)> QuickLinks =
    [
        ("GitHub", "https://github.com/"),
        ("Google", "https://www.google.com/"),
        ("DuckDuckGo", "https://duckduckgo.com/"),
        ("Stack Overflow", "https://stackoverflow.com/")
    ];

    public static BrowserInput Resolve(string raw, Func<string, string> encode)
    {
        var t = raw.Trim();
        if (t.Length == 0) return new BrowserIgnore();
        if (t.StartsWith("http://", StringComparison.OrdinalIgnoreCase) || t.StartsWith("https://", StringComparison.OrdinalIgnoreCase))
            return new BrowserLoad(t);
        if (t.Contains("://")) return new BrowserReject(OnlyHttpMessage);
        if (!t.Contains(' ') && t.Contains('.')) return new BrowserLoad("https://" + t);
        return new BrowserLoad(SearchUrl + encode(t));
    }

    public static bool IsRememberable(string? url) =>
        url is not null && (url.StartsWith("http://", StringComparison.OrdinalIgnoreCase) || url.StartsWith("https://", StringComparison.OrdinalIgnoreCase));

    public static string StartUrl(string? lastUrl) => string.IsNullOrEmpty(lastUrl) ? QuickLinks[0].Url : lastUrl;
}
