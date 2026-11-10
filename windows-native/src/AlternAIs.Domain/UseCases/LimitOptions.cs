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

/// <summary>Una hora del día escrita a mano.</summary>
public sealed record ExactTimeOption(string Label) : LimitOption(Label);

/// <summary>
/// Opciones de Claude: ventana de 5 h y tope semanal.
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
        _ => throw new ArgumentOutOfRangeException(nameof(service))
    };
}
