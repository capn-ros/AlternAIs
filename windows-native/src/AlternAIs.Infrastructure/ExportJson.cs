using System.Text.Json;
using System.Text.Json.Nodes;
using AlternAIs.Application;
using AlternAIs.Domain.Models;
using AlternAIs.Domain.UseCases;

namespace AlternAIs.Infrastructure;

/// <summary>
/// Archivo de exportar/importar cuentas (mismo formato que Android y que la versión Electron). Nunca lleva
/// ids ni sesiones, solo los datos para volver a crear las cuentas.
/// </summary>
public static class ExportJson
{
    public const int MaxChars = 1_000_000;
    private static readonly JsonSerializerOptions Pretty = new() { WriteIndented = true, Encoder = System.Text.Encodings.Web.JavaScriptEncoder.UnsafeRelaxedJsonEscaping };

    public static string Build(AppState state, long nowMs)
    {
        var services = new JsonObject();
        foreach (var service in Enum.GetValues<AiService>())
        {
            var bucket = state.Bucket(service);
            services[service.Key()] = new JsonObject
            {
                ["accounts"] = new JsonArray(bucket.Accounts.Select(a => (JsonNode?)new JsonObject
                {
                    ["name"] = a.Name,
                    ["purpose"] = a.Purpose,
                    ["email"] = a.Email,
                    ["color"] = ColorHex.ToRgb(a.Color),
                    ["limitUntil"] = a.LimitUntil,
                    ["limitStart"] = a.LimitStart,
                    ["lastUrl"] = a.LastUrl,
                    ["selected"] = a.Id == bucket.SelectedId
                }).ToArray())
            };
        }
        return new JsonObject
        {
            ["app"] = "AlternAIs",
            ["version"] = 1,
            ["exportedAt"] = nowMs,
            ["services"] = services
        }.ToJsonString(Pretty);
    }

    public static int CountAccounts(AppState state) => state.Claude.Accounts.Count + state.Replit.Accounts.Count;

    /// <summary>Cuentas del archivo por servicio, o null si el texto no es un archivo válido o es demasiado grande.</summary>
    public static IReadOnlyDictionary<AiService, IReadOnlyList<ImportedAccount>>? Parse(string? json)
    {
        if (string.IsNullOrWhiteSpace(json) || json.Length > MaxChars) return null;
        JsonObject? root;
        try { root = JsonNode.Parse(json) as JsonObject; }
        catch (JsonException) { return null; }
        if (root?["services"] is not JsonObject services) return null;

        var result = new Dictionary<AiService, IReadOnlyList<ImportedAccount>>();
        foreach (var service in Enum.GetValues<AiService>())
        {
            var list = new List<ImportedAccount>();
            if (services[service.Key()]?["accounts"] is JsonArray arr)
            {
                foreach (var node in arr.Take(PlanImport.MaxPerService))
                {
                    if (node is not JsonObject o) { list.Add(new ImportedAccount("")); continue; }
                    list.Add(new ImportedAccount(
                        Str(o["name"]),
                        Str(o["purpose"]),
                        Str(o["color"]),
                        Num(o["limitUntil"]),
                        Num(o["limitStart"]),
                        Str(o["lastUrl"]),
                        Str(o["email"]),
                        o["selected"] is JsonValue s && s.TryGetValue<bool>(out var b) && b));
                }
            }
            result[service] = list;
        }
        return result;
    }

    private static string Str(JsonNode? n) => n is JsonValue v && v.TryGetValue<string>(out var s) ? s : "";

    private static long Num(JsonNode? n)
    {
        if (n is not JsonValue v) return 0;
        if (v.TryGetValue<long>(out var l)) return l;
        return v.TryGetValue<double>(out var d) && d < long.MaxValue && d > long.MinValue ? (long)d : 0;
    }
}
