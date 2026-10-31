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

/// <summary>Todo lo que la app guarda (mismo contenido que el accounts.json de siempre).</summary>
public sealed record AppState(
    ServiceBucket Claude,
    ServiceBucket Replit,
    AiService Service,
    string ClaudeTheme,
    string ReplitTheme,
    bool TutorialShown,
    IReadOnlyList<string> Snippets,
    AppSettings Settings,
    BrowserSettings Browser)
{
    public static AppState Initial { get; } = new(
        ServiceBucket.Empty, ServiceBucket.Empty, AiService.Claude, "dark", "light", false,
        AlternAIs.Domain.Models.Snippets.Defaults, new AppSettings(), new BrowserSettings());

    public ServiceBucket Bucket(AiService s) => s == AiService.Replit ? Replit : Claude;

    public AppState WithBucket(AiService s, ServiceBucket b) =>
        s == AiService.Replit ? this with { Replit = b.Normalized() } : this with { Claude = b.Normalized() };

    public ServiceBucket Current => Bucket(Service);
}

/// <summary>Dónde se guarda el estado (en Windows, un archivo JSON).</summary>
public interface IStateStore
{
    AppState Load();
    void Save(AppState state);
}
