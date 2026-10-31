namespace AlternAIs.Domain.UseCases;

/// <summary>Qué hacer después de cada intento de escribir el mensaje en la página.</summary>
public abstract record SendStep;
public sealed record SendDone : SendStep;
public sealed record SendRetry(int DelayMs) : SendStep;
public sealed record SendGiveUp : SendStep;

/// <summary>Tiempos del envío automático (los mismos que en Android).</summary>
public static class AutoSendPolicy
{
    public const int FirstAttemptDelayMs = 3_000;
    public const int RetryDelayMs = 2_000;
    public const int MaxRetries = 22;
    public const int SettleMs = 3_500;
    public const int TotalTimeoutMs = 80_000;

    public static SendStep Next(int attempt, bool scriptOk) =>
        scriptOk ? new SendDone() : attempt < MaxRetries ? new SendRetry(RetryDelayMs) : new SendGiveUp();
}
