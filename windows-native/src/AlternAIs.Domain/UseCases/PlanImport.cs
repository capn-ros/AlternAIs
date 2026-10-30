using AlternAIs.Domain.Models;
using AlternAIs.Domain.Policies;

namespace AlternAIs.Domain.UseCases;

/// <summary>Una cuenta tal como viene en el archivo de exportación (sin id: los ids se crean al importar).</summary>
public sealed record ImportedAccount(
    string Name,
    string Purpose = "",
    string ColorHex = "",
    long LimitUntil = 0L,
    long LimitStart = 0L,
    string LastUrl = "",
    string Email = "",
    bool Selected = false);

public sealed record ImportPlanResult(IReadOnlyList<Account> Accounts, int Added, int Skipped, string? NewSelectedId);

/// <summary>
/// Reglas de importación (idénticas en Android y Windows): se omiten nombres repetidos (sin importar
/// mayúsculas) o vacíos, máximo 50 por servicio, solo se conservan límites todavía vigentes y nunca
/// se importan sesiones. Si la mitad no tenía cuenta activa, queda activa la marcada en el archivo.
/// </summary>
public sealed class PlanImport(Func<string> newId, IReadOnlyList<int> palette)
{
    public const int MaxPerService = 50;

    public ImportPlanResult Invoke(
        AiService service, IReadOnlyList<Account> existing, IReadOnlyList<ImportedAccount> incoming, bool hasSelected, long now)
    {
        var list = existing.ToList();
        var added = 0;
        var skipped = 0;
        string? selectedId = null;
        foreach (var item in incoming.Take(MaxPerService))
        {
            var name = item.Name.Trim();
            if (name.Length > Account.MaxName) name = name[..Account.MaxName];
            if (name.Length == 0 || list.Any(a => string.Equals(a.Name, name, StringComparison.OrdinalIgnoreCase)))
            {
                skipped++;
                continue;
            }
            var active = item.LimitUntil > now;
            var purpose = item.Purpose.Trim();
            if (purpose.Length > Account.MaxPurpose) purpose = purpose[..Account.MaxPurpose];
            var account = new Account(
                Id: newId(),
                Name: name,
                Color: ColorHex.Parse(item.ColorHex) ?? palette[list.Count % palette.Count],
                Purpose: purpose,
                LimitUntil: active ? item.LimitUntil : 0L,
                LimitStart: active ? item.LimitStart : 0L,
                LastUrl: UrlRestorePolicy.Restorable(service, item.LastUrl) ?? "",
                Email: EmailPolicy.Clean(item.Email));
            if (!hasSelected && selectedId is null && (item.Selected || list.Count == 0)) selectedId = account.Id;
            list.Add(account);
            added++;
        }
        return new ImportPlanResult(list, added, skipped, selectedId);
    }
}
