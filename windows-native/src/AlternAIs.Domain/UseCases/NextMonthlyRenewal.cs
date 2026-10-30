namespace AlternAIs.Domain.UseCases;

/// <summary>Renovación mensual (Replit): próximo día del mes a las 00:00; si el mes es más corto, el último día.</summary>
public static class NextMonthlyRenewal
{
    public static DateTimeOffset? Invoke(int dayOfMonth, DateTimeOffset now, TimeZoneInfo zone)
    {
        if (dayOfMonth is < 1 or > 31) return null;

        DateTimeOffset InMonth(int year, int month)
        {
            var day = Math.Min(dayOfMonth, DateTime.DaysInMonth(year, month));
            var local = new DateTime(year, month, day, 0, 0, 0, DateTimeKind.Unspecified);
            return new DateTimeOffset(local, zone.GetUtcOffset(local));
        }

        var localNow = TimeZoneInfo.ConvertTime(now, zone);
        var current = InMonth(localNow.Year, localNow.Month);
        if (current > now) return current;
        var next = new DateTime(localNow.Year, localNow.Month, 1).AddMonths(1);
        return InMonth(next.Year, next.Month);
    }
}
