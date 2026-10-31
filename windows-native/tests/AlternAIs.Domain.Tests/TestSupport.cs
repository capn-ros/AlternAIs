using AlternAIs.Domain;
using AlternAIs.Domain.Models;

namespace AlternAIs.Domain.Tests;

/// <summary>Cosas compartidas por los tests: una zona fija (UTC-3, sin horario de verano) y un reloj de mentira.</summary>
internal static class TestSupport
{
    /// <summary>Hora de Buenos Aires: UTC-3 todo el año. Se crea a mano para no depender de los nombres de zona del sistema.</summary>
    public static readonly TimeZoneInfo Zone = TimeZoneInfo.CreateCustomTimeZone("AR", TimeSpan.FromHours(-3), "AR", "AR");

    /// <summary>29/10/2026 a la hora indicada, en la zona de prueba.</summary>
    public static DateTimeOffset At(int hour, int minute = 0, int day = 29, int month = 10, int year = 2026) =>
        new(year, month, day, hour, minute, 0, TimeSpan.FromHours(-3));

    public static Account Acc(string id, long until = 0, string name = "")
        => new(id, name.Length == 0 ? "n" + id : name, Color: 1, LimitUntil: until, LimitStart: until > 0 ? 1 : 0);
}

internal sealed class FakeClock(long nowMs) : IClock
{
    public long NowMs { get; set; } = nowMs;
    public TimeZoneInfo Zone => TestSupport.Zone;
}
