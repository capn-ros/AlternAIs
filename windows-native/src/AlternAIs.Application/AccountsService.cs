using AlternAIs.Domain;
using AlternAIs.Domain.Models;
using AlternAIs.Domain.UseCases;

namespace AlternAIs.Application;

/// <summary>Resultado de importar un archivo de cuentas.</summary>
public sealed record ImportSummary(int Added, int Skipped);

/// <summary>
/// Guarda y cambia las cuentas aplicando las reglas del dominio. Cada operación deja el estado guardado.
/// Es el único lugar que modifica cuentas: la interfaz solo lo llama.
/// </summary>
public sealed class AccountsService
{
    private readonly IStateStore _store;
    private readonly IClock _clock;
    private readonly Func<string> _newId;

    public AccountsService(IStateStore store, IClock clock, Func<string>? newId = null)
    {
        _store = store;
        _clock = clock;
        _newId = newId ?? Account.NewId;
        State = store.Load();
    }

    public AppState State { get; private set; }

    public event Action<AppState>? Changed;

    public IReadOnlyList<Account> Accounts => State.Current.Accounts;
    public string? SelectedId => State.Current.SelectedId;
    public Account? Selected => Accounts.FirstOrDefault(a => a.Id == SelectedId);

    private void Commit(AppState next)
    {
        State = next;
        _store.Save(next);
        Changed?.Invoke(next);
    }

    private void Update(Func<ServiceBucket, ServiceBucket> change) =>
        Commit(State.WithBucket(State.Service, change(State.Current)));

    /// <summary>Cambia de servicio (Claude ↔ Replit).</summary>
    public void SwitchService(AiService service)
    {
        if (service != State.Service) Commit(State with { Service = service });
    }

    public void Select(string id)
    {
        if (Accounts.All(a => a.Id != id) || id == SelectedId) return;
        Update(b => b with { SelectedId = id });
    }

    /// <summary>Pasa a la cuenta siguiente (+1) o anterior (-1). Devuelve el id elegido, o null si no hay a dónde ir.</summary>
    public string? Step(int delta)
    {
        var next = AccountNavigation.Step(Accounts.Select(a => a.Id).ToList(), SelectedId, delta);
        if (next is not null) Select(next);
        return next;
    }

    /// <summary>Agrega una cuenta nueva y la deja activa. Devuelve null si ya hay 50.</summary>
    public Account? Add(AccountFormValid form)
    {
        if (Accounts.Count >= PlanImport.MaxPerService) return null;
        var account = new Account(_newId(), form.Name, form.Color, form.Purpose, Email: form.Email);
        Update(b => new ServiceBucket([.. b.Accounts, account], account.Id));
        return account;
    }

    /// <summary>Edita nombre, propósito, correo y color conservando el id y la sesión.</summary>
    public void Edit(string id, AccountFormValid form)
    {
        if (Accounts.All(a => a.Id != id)) return;
        Update(b => b with
        {
            Accounts = b.Accounts.Select(a => a.Id == id
                ? a with { Name = form.Name, Purpose = form.Purpose, Email = form.Email, Color = form.Color }
                : a).ToList()
        });
    }

    /// <summary>Quita la cuenta del listado. Borrar su sesión (cookies) lo hace la capa de interfaz.</summary>
    public void Remove(string id)
    {
        if (Accounts.All(a => a.Id != id)) return;
        Update(b => new ServiceBucket(b.Accounts.Where(a => a.Id != id).ToList(), b.SelectedId));
    }

    /// <summary>Marca el límite hasta [untilMs]. Si es en el pasado no hace nada.</summary>
    public void MarkLimit(string id, long untilMs)
    {
        var marker = new MarkLimit(_clock);
        ReplaceAccount(id, a => marker.Invoke(a, untilMs));
    }

    public void ClearLimit(string id) => ReplaceAccount(id, a => a.WithoutLimit());

    public void SetAutoReply(string id, string text) =>
        ReplaceAccount(id, a => a with { AutoReply = text.Trim().Length > 4000 ? text.Trim()[..4000] : text.Trim() });

    /// <summary>Servicio al que pertenece una cuenta (puede no ser el que se está viendo).</summary>
    public AiService? ServiceOf(string id) =>
        Enum.GetValues<AiService>().Where(sv => State.Bucket(sv).Accounts.Any(a => a.Id == id)).Select(sv => (AiService?)sv).FirstOrDefault();

    public void RememberUrl(string id, string? url)
    {
        if (ServiceOf(id) is not { } service) return;
        var clean = AlternAIs.Domain.Policies.UrlRestorePolicy.Restorable(service, url);
        if (clean is null) return;
        ReplaceAccount(id, a => a with { LastUrl = clean });
    }

    private void ReplaceAccount(string id, Func<Account, Account> change)
    {
        if (ServiceOf(id) is not { } service) return;
        var bucket = State.Bucket(service);
        var current = bucket.Accounts.First(a => a.Id == id);
        var changed = change(current);
        if (changed == current) return;
        Commit(State.WithBucket(service, bucket with { Accounts = bucket.Accounts.Select(a => a.Id == id ? changed : a).ToList() }));
    }

    /// <summary>Limpia los descansos vencidos de ambos servicios. Devuelve las cuentas que terminaron.</summary>
    public IReadOnlyList<Account> ExpireLimits()
    {
        var expire = new ExpireLimits(_clock);
        var all = new List<Account>();
        var next = State;
        foreach (var service in Enum.GetValues<AiService>())
        {
            var result = expire.Invoke(next.Bucket(service).Accounts);
            if (result.Expired.Count == 0) continue;
            all.AddRange(result.Expired);
            next = next.WithBucket(service, next.Bucket(service) with { Accounts = result.Accounts });
        }
        if (all.Count > 0) Commit(next);
        return all;
    }

    /// <summary>Primera cuenta libre distinta de la actual (para saltar cuando una llega al límite).</summary>
    public Account? FirstAvailableExceptCurrent() => Accounts.FirstAvailableExcept(SelectedId, _clock.NowMs);

    /// <summary>Importa cuentas de un archivo (reglas de <see cref="PlanImport"/>, iguales en Android).</summary>
    public ImportSummary Import(IReadOnlyDictionary<AiService, IReadOnlyList<ImportedAccount>> incoming)
    {
        var planner = new PlanImport(_newId, AccountPalette.Colors);
        var next = State;
        int added = 0, skipped = 0;
        foreach (var service in Enum.GetValues<AiService>())
        {
            if (!incoming.TryGetValue(service, out var list)) continue;
            var bucket = next.Bucket(service);
            var plan = planner.Invoke(service, bucket.Accounts, list, bucket.SelectedId is not null, _clock.NowMs);
            added += plan.Added;
            skipped += plan.Skipped;
            next = next.WithBucket(service, new ServiceBucket(plan.Accounts, plan.NewSelectedId ?? bucket.SelectedId));
        }
        if (added > 0) Commit(next);
        return new ImportSummary(added, skipped);
    }

    public void SetTheme(AiService service, string theme)
    {
        var t = theme == "light" ? "light" : "dark";
        Commit(service == AiService.Replit ? State with { ReplitTheme = t } : State with { ClaudeTheme = t });
    }

    public void MarkTutorialShown() => Commit(State with { TutorialShown = true });

    public void SetSnippets(IEnumerable<string> snippets) =>
        Commit(State with { Snippets = AlternAIs.Domain.Models.Snippets.Normalize(snippets) });

    public void SetSettings(AppSettings settings) => Commit(State with { Settings = settings });

    public void SetBrowser(BrowserSettings browser) => Commit(State with { Browser = browser });
}
