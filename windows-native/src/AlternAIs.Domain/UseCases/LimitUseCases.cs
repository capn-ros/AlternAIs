using AlternAIs.Domain.Models;

namespace AlternAIs.Domain.UseCases;

/// <summary>Marca una cuenta en descanso hasta <c>untilMs</c>. Si ese momento ya pasó, no cambia nada.</summary>
public sealed class MarkLimit(IClock clock)
{
    public Account Invoke(Account account, long untilMs)
    {
        var now = clock.NowMs;
        return untilMs > now ? account with { LimitUntil = untilMs, LimitStart = now } : account;
    }
}

/// <summary>Resultado de limpiar descansos vencidos: la lista nueva y las cuentas que acaban de quedar disponibles.</summary>
public sealed record ExpiredLimits(IReadOnlyList<Account> Accounts, IReadOnlyList<Account> Expired);

public sealed class ExpireLimits(IClock clock)
{
    public ExpiredLimits Invoke(IReadOnlyList<Account> accounts)
    {
        var now = clock.NowMs;
        var expired = accounts.Where(a => a.LimitUntil > 0L && a.LimitUntil <= now).ToList();
        if (expired.Count == 0) return new ExpiredLimits(accounts, []);
        var ids = expired.Select(a => a.Id).ToHashSet();
        return new ExpiredLimits(accounts.Select(a => ids.Contains(a.Id) ? a.WithoutLimit() : a).ToList(), expired);
    }
}

public static class AccountLimitExtensions
{
    public static Account WithoutLimit(this Account account) => account with { LimitUntil = 0L, LimitStart = 0L };

    /// <summary>Primera cuenta disponible distinta de la actual (para el aviso «Ir a otra cuenta disponible»).</summary>
    public static Account? FirstAvailableExcept(this IEnumerable<Account> accounts, string? currentId, long now) =>
        accounts.FirstOrDefault(a => a.Id != currentId && !a.IsResting(now));
}
