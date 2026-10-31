namespace AlternAIs.Domain.UseCases;

/// <summary>Cuánto falta, en palabras cortas: "2 h 10 min", "3 d 4 h", "12 min" (nunca menos de 1 min).</summary>
public static class RemainingTime
{
    public static string Format(long ms)
    {
        var totalMin = Math.Max(ms / 60_000L, 1L);
        var days = totalMin / (60 * 24);
        var hours = totalMin % (60 * 24) / 60;
        var mins = totalMin % 60;
        if (days > 0) return $"{days} d {hours} h";
        if (hours > 0) return $"{hours} h {mins} min";
        return $"{mins} min";
    }
}
