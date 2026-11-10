using AlternAIs.Domain.Models;
using AlternAIs.Domain.UseCases;

namespace AlternAIs.Application;

public enum TrayEntryKind { Open, Header, Account, Separator, Quit }

/// <summary>Una línea del menú de la bandeja. <paramref name="AccountId"/> solo en las de cuenta.</summary>
public sealed record TrayEntry(TrayEntryKind Kind, string Text, string? AccountId = null, AiService? Service = null);

public sealed record TrayMenuModel(string Tooltip, IReadOnlyList<TrayEntry> Entries);

/// <summary>
/// Menú del ícono de la bandeja (igual que la versión Electron): abrir, cada cuenta con su
/// cuenta regresiva o «Disponible», y salir. El globo dice cuántas están libres.
/// </summary>
public static class TrayMenu
{
    public const string AppName = "AlternAIs";

    public static TrayMenuModel Build(AppState state, long now)
    {
        var entries = new List<TrayEntry> { new(TrayEntryKind.Open, "Abrir AlternAIs"), new(TrayEntryKind.Separator, "") };
        int free = 0, total = 0;
        foreach (var service in Enum.GetValues<AiService>())
        {
            var list = state.Bucket(service).Accounts;
            if (list.Count == 0) continue;
            foreach (var a in list)
            {
                total++;
                var resting = a.IsResting(now);
                if (!resting) free++;
                var text = (resting ? "⏳  " : "✅  ") + a.Name + " — " + (resting ? RemainingTime.Format(a.LimitUntil - now) : "Disponible");
                entries.Add(new TrayEntry(TrayEntryKind.Account, text, a.Id, service));
            }
            entries.Add(new TrayEntry(TrayEntryKind.Separator, ""));
        }
        entries.Add(new TrayEntry(TrayEntryKind.Quit, "Salir"));
        var tip = total > 0 ? $"{AppName} — {free} de {total} disponibles" : AppName;
        return new TrayMenuModel(tip, entries);
    }
}
