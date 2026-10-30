using System.Text.RegularExpressions;

namespace AlternAIs.Domain.Policies;

/// <summary>Correo opcional para recordar con cuál se inicia sesión. Nunca se guardan contraseñas.</summary>
public static partial class EmailPolicy
{
    public const int MaxLength = 80;

    [GeneratedRegex(@"^[^\s@]+@[^\s@]+\z")]
    private static partial Regex Shape();

    /// <summary>El correo recortado, o "" si no tiene forma de correo.</summary>
    public static string Clean(string? raw)
    {
        var t = (raw ?? "").Trim();
        if (t.Length > MaxLength) t = t[..MaxLength];
        return Shape().IsMatch(t) ? t : "";
    }
}
