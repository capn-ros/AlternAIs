using AlternAIs.Domain.Models;

namespace AlternAIs.Domain.UseCases;

/// <summary>Una de las maneras de indicar cuándo se reinicia el límite de uso.</summary>
public abstract record LimitOption(string Label);

/// <summary>Dentro de <c>Hours</c> horas a partir de ahora.</summary>
public sealed record AfterHours(string Label, long Hours) : LimitOption(Label)
{
    private const long HourMs = 3_600_000L;
    public long UntilMs(long now) => now + Hours * HourMs;
}

/// <summary>Replit: día de renovación mensual de los créditos (a las 00:00).</summary>
public sealed record MonthlyRenewalOption(string Label) : LimitOption(Label);

/// <summary>Una hora del día escrita a mano.</summary>
public sealed record ExactTimeOption(string Label) : LimitOption(Label);

/// <summary>
/// Opciones según la cuota de cada IA. Claude: ventana de 5 h y tope semanal. Replit: Modo gratis
/// (se renueva cada 5 h y tiene tope semanal), créditos diarios del plan Starter y créditos mensuales
/// que se renuevan al empezar cada ciclo de facturación.
/// </summary>
public static class LimitOptions
{
    public static IReadOnlyList<LimitOption> ForService(AiService service) => service switch
    {
        AiService.Claude =>
        [
            new AfterHours("Dentro de 5 horas", 5),
            new AfterHours("Dentro de 24 horas", 24),
            new AfterHours("Dentro de 7 días", 7 * 24),
            new ExactTimeOption("A una hora exacta…")
        ],
        AiService.Replit =>
        [
            new AfterHours("Dentro de 5 horas (Modo gratis)", 5),
            new AfterHours("Dentro de 24 horas (créditos diarios)", 24),
            new AfterHours("Dentro de 7 días (tope semanal)", 7 * 24),
            new MonthlyRenewalOption("En la renovación mensual…"),
            new ExactTimeOption("A una hora exacta…")
        ],
        _ => throw new ArgumentOutOfRangeException(nameof(service))
    };
}
