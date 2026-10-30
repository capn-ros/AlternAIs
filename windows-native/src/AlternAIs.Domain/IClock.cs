namespace AlternAIs.Domain;

/// <summary>Reloj inyectable: los casos de uso no llaman a DateTime.Now, así se prueban sin esperar.</summary>
public interface IClock
{
    /// <summary>Milisegundos desde 1970 (UTC), igual que los límites guardados en las cuentas.</summary>
    long NowMs { get; }

    TimeZoneInfo Zone { get; }
}

public sealed class SystemClock : IClock
{
    public long NowMs => DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
    public TimeZoneInfo Zone => TimeZoneInfo.Local;
}
