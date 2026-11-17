using AlternAIs.Domain.Models;

namespace AlternAIs.Application;

/// <summary>Cuentas de un servicio y cuál está activa.</summary>
public sealed record ServiceBucket(IReadOnlyList<Account> Accounts, string? SelectedId)
{
    public static ServiceBucket Empty { get; } = new([], null);

    /// <summary>Si la cuenta elegida ya no existe queda la primera (o ninguna).</summary>
    public ServiceBucket Normalized() =>
        Accounts.Any(a => a.Id == SelectedId) ? this : this with { SelectedId = Accounts.Count > 0 ? Accounts[0].Id : null };
}

public sealed record AppSettings(string RailPos = "left", bool LaunchAtLogin = false, bool CloseToTray = false);

/// <param name="DlMode">default, ask o folder.</param>
public sealed record BrowserSettings(string LastUrl = "", string DlMode = "default", string DlDir = "");

/// <summary>
/// Todo lo que la app guarda (mismo contenido que el accounts.json de siempre). Claude y su tema van en campos
/// propios (formato de siempre); las demás IAs, en diccionarios. <see cref="Active"/> es el servicio que se está
/// viendo: no se guarda, la app siempre abre en Claude.
/// </summary>
public sealed record AppState(
    ServiceBucket Claude,
    string ClaudeTheme,
    bool TutorialShown,
    IReadOnlyList<string> Snippets,
    AppSettings Settings,
    BrowserSettings Browser,
    AiService Active = AiService.Claude,
    IReadOnlyDictionary<AiService, ServiceBucket>? OtherBuckets = null,
    IReadOnlyDictionary<AiService, string>? OtherThemes = null)
{
    public static AppState Initial { get; } = new(
        ServiceBucket.Empty, "dark", false,
        AlternAIs.Domain.Models.Snippets.Defaults, new AppSettings(), new BrowserSettings());

    /// <summary>El servicio que se está viendo.</summary>
    public AiService Service => Active;

    public ServiceBucket Bucket(AiService s) =>
        s == AiService.Claude ? Claude
        : OtherBuckets is not null && OtherBuckets.TryGetValue(s, out var b) ? b
        : ServiceBucket.Empty;

    public AppState WithBucket(AiService s, ServiceBucket b)
    {
        var n = b.Normalized();
        if (s == AiService.Claude) return this with { Claude = n };
        var d = (OtherBuckets ?? new Dictionary<AiService, ServiceBucket>()).ToDictionary(kv => kv.Key, kv => kv.Value);
        d[s] = n;
        return this with { OtherBuckets = d };
    }

    public ServiceBucket Current => Bucket(Service);

    /// <summary>Tema («dark» o «light») del servicio; si nunca se eligió, el de arranque de ese servicio.</summary>
    public string Theme(AiService s) =>
        s == AiService.Claude ? ClaudeTheme
        : OtherThemes is not null && OtherThemes.TryGetValue(s, out var t) ? t
        : s.DefaultTheme();

    public AppState WithTheme(AiService s, string theme)
    {
        if (s == AiService.Claude) return this with { ClaudeTheme = theme };
        var d = (OtherThemes ?? new Dictionary<AiService, string>()).ToDictionary(kv => kv.Key, kv => kv.Value);
        d[s] = theme;
        return this with { OtherThemes = d };
    }

    /// <summary>Cantidad de cuentas en todos los servicios.</summary>
    public int TotalAccounts => Enum.GetValues<AiService>().Sum(s => Bucket(s).Accounts.Count);
}

/// <summary>Dónde se guarda el estado (en Windows, un archivo JSON).</summary>
public interface IStateStore
{
    AppState Load();
    void Save(AppState state);
}
