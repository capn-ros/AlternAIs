using System.Security.Cryptography;

namespace AlternAIs.Domain.Models;

/// <summary>Cuenta inmutable. Los cambios se hacen con <c>with</c> desde los casos de uso.</summary>
/// <param name="Color">Color ARGB.</param>
/// <param name="LimitUntil">Epoch ms hasta el que la cuenta descansa por límite de uso. 0 = disponible.</param>
/// <param name="LimitStart">Epoch ms en que se marcó el límite. 0 = no se sabe (marcas viejas).</param>
/// <param name="AutoReply">Mensaje que se envía solo al terminar el descanso ("" = ninguno).</param>
public sealed record Account(
    string Id,
    string Name,
    int Color,
    string Purpose = "",
    long LimitUntil = 0L,
    long LimitStart = 0L,
    string LastUrl = "",
    string Email = "",
    string AutoReply = "")
{
    public const int MaxName = 24;
    public const int MaxPurpose = 60;

    private const long Hour = 3_600_000L;
    private static readonly long[] StandardPeriods = [5 * Hour, 24 * Hour, 7 * 24 * Hour, 30 * 24 * Hour];

    /// <summary>Cada cuenta usa una partición propia de WebView. No cambiar nunca el id de una cuenta existente.</summary>
    public string ProfileName => $"acc_{Id}";

    public bool IsResting(long now) => LimitUntil > now;

    /// <summary>
    /// Qué fracción del descanso falta (1 = recién marcado, 0 = terminó). Sin inicio guardado se supone
    /// el plazo estándar más cercano (5 h, 24 h, 7 d o 30 d).
    /// </summary>
    public float LimitFraction(long now)
    {
        if (!IsResting(now)) return 0f;
        var remaining = LimitUntil - now;
        long total;
        if (LimitStart > 0 && LimitStart < LimitUntil)
        {
            total = LimitUntil - LimitStart;
        }
        else
        {
            total = remaining;
            foreach (var period in StandardPeriods)
            {
                if (period >= remaining) { total = period; break; }
            }
        }
        return Math.Clamp((float)remaining / total, 0f, 1f);
    }

    /// <summary>Id nuevo: 12 caracteres hexadecimales al azar (igual que las cuentas de siempre).</summary>
    public static string NewId() => Convert.ToHexString(RandomNumberGenerator.GetBytes(6)).ToLowerInvariant();
}
