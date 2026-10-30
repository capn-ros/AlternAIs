using AlternAIs.Application;
using AlternAIs.Domain;
using AlternAIs.Domain.Models;

namespace AlternAIs.Application.Tests;

internal sealed class FakeClock(long nowMs) : IClock
{
    public long NowMs { get; set; } = nowMs;
    public TimeZoneInfo Zone => TimeZoneInfo.Utc;
}

internal sealed class MemoryStore(AppState? initial = null) : IStateStore
{
    public AppState Saved { get; private set; } = initial ?? AppState.Initial;
    public int Saves { get; private set; }
    public AppState Load() => Saved;
    public void Save(AppState state) { Saved = state; Saves++; }
}

internal sealed class IdSource
{
    private int _n;
    public string Next() => $"{++_n:x12}";
}

internal static class T
{
    public const long Now = 1_800_000_000_000L;
    public const long Hour = 3_600_000L;

    public static Account Acc(string id, string name = "", long until = 0) =>
        new(id, name.Length == 0 ? "n" + id : name, unchecked((int)0xFFE07A5F), LimitUntil: until, LimitStart: until > 0 ? Now - Hour : 0);
}
