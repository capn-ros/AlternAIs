using System.Text.Json;
using System.Text.Json.Nodes;
using System.Text.RegularExpressions;
using AlternAIs.Application;
using AlternAIs.Domain.Models;
using AlternAIs.Domain.Policies;

namespace AlternAIs.Infrastructure;

/// <summary>
/// Lee y escribe el estado en el mismo formato del <c>accounts.json</c> de la versión Electron, con la
/// misma limpieza de datos: así las cuentas, los colores y los ids (y con ellos las sesiones) se conservan.
/// </summary>
public static partial class StateJson
{
    [GeneratedRegex("^[a-f0-9]{6,32}$")]
    private static partial Regex IdShape();

    [GeneratedRegex(@"^#[0-9a-fA-F]{6}$")]
    private static partial Regex RgbShape();

    private static readonly JsonSerializerOptions Pretty = new() { WriteIndented = true, Encoder = System.Text.Encodings.Web.JavaScriptEncoder.UnsafeRelaxedJsonEscaping };

    public const string DefaultColorHex = "#E07A5F";

    /// <summary>Estado leído del texto; si está vacío, roto o no es un objeto devuelve el estado inicial.</summary>
    public static AppState Parse(string? json)
    {
        JsonObject? root;
        try { root = string.IsNullOrWhiteSpace(json) ? null : JsonNode.Parse(json) as JsonObject; }
        catch (JsonException) { root = null; }
        if (root is null) return AppState.Initial;

        var claude = Bucket(root["accounts"], root["selectedId"], AiService.Claude);
        var replitNode = root["replit"] as JsonObject;
        var replit = replitNode is null
            ? ServiceBucket.Empty
            : Bucket(replitNode["accounts"], replitNode["selectedId"], AiService.Replit);

        var themes = root["themes"] as JsonObject;
        var settings = root["settings"] as JsonObject;
        var browser = root["browser"] as JsonObject;
        var snippets = root["snippets"] is JsonArray arr
            ? Snippets.Normalize(arr.Select(n => Str(n)).Where(s => s is not null).Select(s => s!))
            : Snippets.Defaults;

        return new AppState(
            claude.Normalized(),
            replit.Normalized(),
            Str(root["service"]) == "replit" ? AiService.Replit : AiService.Claude,
            Str(themes?["claude"]) == "light" ? "light" : "dark",
            Str(themes?["replit"]) == "dark" ? "dark" : "light",
            root["tutorialShown"] is JsonValue t && t.TryGetValue<bool>(out var shown) && shown,
            snippets,
            new AppSettings(
                (Str(settings?["railPos"]) is "left" or "right" or "bottom") ? Str(settings?["railPos"])! : "left",
                Bool(settings?["launchAtLogin"]),
                Bool(settings?["closeToTray"])),
            new BrowserSettings(
                CleanBrowserUrl(Str(browser?["lastUrl"])),
                (Str(browser?["dlMode"]) is "default" or "ask" or "folder") ? Str(browser?["dlMode"])! : "default",
                CleanDir(Str(browser?["dlDir"]))));
    }

    /// <summary>El estado como texto JSON, en el formato de siempre.</summary>
    public static string Serialize(AppState s)
    {
        var obj = new JsonObject
        {
            ["accounts"] = AccountsArray(s.Claude.Accounts),
            ["selectedId"] = s.Claude.SelectedId,
            ["replit"] = new JsonObject
            {
                ["accounts"] = AccountsArray(s.Replit.Accounts),
                ["selectedId"] = s.Replit.SelectedId
            },
            ["service"] = s.Service.Key(),
            ["themes"] = new JsonObject { ["claude"] = s.ClaudeTheme, ["replit"] = s.ReplitTheme },
            ["tutorialShown"] = s.TutorialShown,
            ["snippets"] = new JsonArray(s.Snippets.Select(x => (JsonNode?)JsonValue.Create(x)).ToArray()),
            ["settings"] = new JsonObject
            {
                ["railPos"] = s.Settings.RailPos,
                ["launchAtLogin"] = s.Settings.LaunchAtLogin,
                ["closeToTray"] = s.Settings.CloseToTray
            },
            ["browser"] = new JsonObject
            {
                ["lastUrl"] = s.Browser.LastUrl,
                ["dlMode"] = s.Browser.DlMode,
                ["dlDir"] = s.Browser.DlDir
            }
        };
        return obj.ToJsonString(Pretty);
    }

    private static JsonArray AccountsArray(IReadOnlyList<Account> accounts) =>
        new(accounts.Select(a => (JsonNode?)new JsonObject
        {
            ["id"] = a.Id,
            ["name"] = a.Name,
            ["purpose"] = a.Purpose,
            ["color"] = ColorHex.ToRgb(a.Color),
            ["limitUntil"] = a.LimitUntil,
            ["limitStart"] = a.LimitStart,
            ["lastUrl"] = a.LastUrl,
            ["email"] = a.Email,
            ["autoReply"] = a.AutoReply
        }).ToArray());

    private static ServiceBucket Bucket(JsonNode? accounts, JsonNode? selected, AiService service)
    {
        var list = accounts is JsonArray arr
            ? arr.Select(n => ParseAccount(n as JsonObject, service)).Where(a => a is not null).Select(a => a!).ToList()
            : [];
        return new ServiceBucket(list, Str(selected));
    }

    private static Account? ParseAccount(JsonObject? o, AiService service)
    {
        if (o is null) return null;
        var id = Str(o["id"]);
        if (id is null || !IdShape().IsMatch(id)) return null;
        var name = Text(o["name"], AlternAIs.Domain.Models.Account.MaxName);
        var color = Str(o["color"]);
        return new Account(
            id,
            name.Length == 0 ? "Cuenta" : name,
            ColorHex.Parse(color is not null && RgbShape().IsMatch(color) ? color : DefaultColorHex)!.Value,
            Text(o["purpose"], AlternAIs.Domain.Models.Account.MaxPurpose),
            PositiveLong(o["limitUntil"]),
            PositiveLong(o["limitStart"]),
            UrlRestorePolicy.Restorable(service, Str(o["lastUrl"])) ?? "",
            EmailPolicy.Clean(Str(o["email"])),
            Text(o["autoReply"], 4000));
    }

    private static string? Str(JsonNode? n) =>
        n is JsonValue v && v.TryGetValue<string>(out var s) ? s : null;

    private static bool Bool(JsonNode? n) => n is JsonValue v && v.TryGetValue<bool>(out var b) && b;

    private static string Text(JsonNode? n, int max)
    {
        var t = (Str(n) ?? "").Trim();
        return t.Length > max ? t[..max] : t;
    }

    private static long PositiveLong(JsonNode? n)
    {
        if (n is not JsonValue v) return 0;
        if (v.TryGetValue<long>(out var l)) return l > 0 ? l : 0;
        if (v.TryGetValue<double>(out var d)) return d > 0 && d < long.MaxValue ? (long)d : 0;
        return 0;
    }

    private static string CleanBrowserUrl(string? raw)
    {
        if (raw is null || !(raw.StartsWith("http://", StringComparison.OrdinalIgnoreCase) || raw.StartsWith("https://", StringComparison.OrdinalIgnoreCase)))
            return "";
        return raw.Length > 2000 ? raw[..2000] : raw;
    }

    private static string CleanDir(string? raw) => raw is not null && Path.IsPathRooted(raw) ? raw : "";
}
