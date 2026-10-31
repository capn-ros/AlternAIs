namespace AlternAIs.Domain.UseCases;

public enum DayPeriod { Auto, Am, Pm }

/// <summary>
/// «Hora exacta» del límite. Con Auto elige AM o PM según lo próximo que viene (a las 16:30, «5» es
/// 17:00 de hoy). Horas 0 o mayores de 12 se toman como formato de 24 h. Si la hora ya pasó, es mañana.
/// Devuelve null si la hora o los minutos son inválidos.
/// </summary>
public static class ResolveExactTime
{
    public static DateTimeOffset? Invoke(int hour, int minute, DayPeriod period, DateTimeOffset now, TimeZoneInfo zone)
    {
        if (hour is < 0 or > 23 || minute is < 0 or > 59) return null;
        int[] hours;
        if (hour == 0 || hour > 12) hours = new[] { hour };
        else if (period == DayPeriod.Am) hours = new[] { hour % 12 };
        else if (period == DayPeriod.Pm) hours = new[] { hour % 12 + 12 };
        else hours = new[] { hour % 12, hour % 12 + 12 };
        Array.Sort(hours);

        var localNow = TimeZoneInfo.ConvertTime(now, zone).DateTime;
        for (var dayOffset = 0; dayOffset <= 1; dayOffset++)
        {
            foreach (var h in hours)
            {
                var local = localNow.Date.AddDays(dayOffset).AddHours(h).AddMinutes(minute);
                var candidate = new DateTimeOffset(local, zone.GetUtcOffset(local));
                if (candidate > now) return candidate;
            }
        }
        return null;
    }
}
