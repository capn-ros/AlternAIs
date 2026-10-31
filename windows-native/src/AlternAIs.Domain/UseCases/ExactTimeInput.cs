using System.Globalization;

namespace AlternAIs.Domain.UseCases;

/// <summary>Texto escrito en el diálogo de «hora exacta»: lo convierte en un momento y arma la vista previa.</summary>
public static class ExactTimeInput
{
    /// <summary>Campos vacíos valen 0. Devuelve null si no son números o están fuera de rango.</summary>
    public static DateTimeOffset? Resolve(string hourText, string minuteText, DayPeriod period, DateTimeOffset now, TimeZoneInfo zone)
    {
        if (!TryNumber(hourText, out var hour) || !TryNumber(minuteText, out var minute)) return null;
        return ResolveExactTime.Invoke(hour, minute, period, now, zone);
    }

    /// <summary>«Se reinicia hoy a las 17:00» / «Se reinicia mañana a las 05:00» / «Hora no válida».</summary>
    public static string Describe(DateTimeOffset? target, DateTimeOffset now, TimeZoneInfo zone)
    {
        if (target is null) return "Hora no válida";
        var local = TimeZoneInfo.ConvertTime(target.Value, zone);
        var today = TimeZoneInfo.ConvertTime(now, zone).Date;
        var day = local.Date == today ? "hoy" : "mañana";
        return $"Se reinicia {day} a las {local.Hour:00}:{local.Minute:00}";
    }

    private static bool TryNumber(string text, out int value)
    {
        var t = text.Length == 0 ? "0" : text;
        return int.TryParse(t, NumberStyles.AllowLeadingSign, CultureInfo.InvariantCulture, out value);
    }
}
