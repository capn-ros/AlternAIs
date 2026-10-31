using System.Globalization;
using System.Text.RegularExpressions;

namespace AlternAIs.Domain.Models;

/// <summary>Los 8 colores que se pueden elegir para una cuenta (ARGB opaco). Mismos valores que en Android.</summary>
public static class AccountPalette
{
    public static readonly IReadOnlyList<int> Colors = new int[]
    {
        unchecked((int)0xFFE07A5F), unchecked((int)0xFFF2A541), unchecked((int)0xFF81B29A), unchecked((int)0xFF4FB0C6),
        unchecked((int)0xFF6C8EAD), unchecked((int)0xFF9A7BD1), unchecked((int)0xFFD96C9F), unchecked((int)0xFF8D99AE)
    };

    /// <summary>Color sugerido para una cuenta nueva: se reparten en orden según cuántas hay.</summary>
    public static int DefaultFor(int existingCount) => Colors[Math.Max(existingCount, 0) % Colors.Count];

    /// <summary>Colores del degradé del botón «+» (vuelve al primero para cerrar el círculo).</summary>
    public static IReadOnlyList<int> SweepColors() => [.. Colors, Colors[0]];
}

/// <summary>Colores en texto «#RRGGBB» o «#AARRGGBB», como en los archivos de exportación.</summary>
public static partial class ColorHex
{
    [GeneratedRegex(@"^#[0-9a-fA-F]{6}\z")]
    private static partial Regex Rgb();

    [GeneratedRegex(@"^#[0-9a-fA-F]{8}\z")]
    private static partial Regex Argb();

    /// <summary>Null si no tiene forma de color. «#RRGGBB» queda opaco.</summary>
    public static int? Parse(string? hex)
    {
        if (hex is null) return null;
        if (Rgb().IsMatch(hex))
            return unchecked((int)(0xFF000000u | uint.Parse(hex.AsSpan(1), NumberStyles.HexNumber, CultureInfo.InvariantCulture)));
        if (Argb().IsMatch(hex))
            return unchecked((int)uint.Parse(hex.AsSpan(1), NumberStyles.HexNumber, CultureInfo.InvariantCulture));
        return null;
    }

    /// <summary>«#RRGGBB» (sin transparencia), el formato de la exportación.</summary>
    public static string ToRgb(int argb) => "#" + (argb & 0xFFFFFF).ToString("X6", CultureInfo.InvariantCulture);
}
