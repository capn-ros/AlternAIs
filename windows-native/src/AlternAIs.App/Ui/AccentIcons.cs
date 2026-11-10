using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Windows.UI;

namespace AlternAIs.App.Ui;

/// <summary>
/// Íconos de botones y opciones del color de la cuenta activa (como en Android). Cada ícono creado con
/// <see cref="Create"/> se registra (referencia débil) y <see cref="Apply"/> los repinta todos al cambiar de cuenta.
/// Los íconos de carpetas y archivos del explorador NO pasan por acá: conservan su color por tipo.
/// </summary>
internal static class AccentIcons
{
    private static readonly List<WeakReference<FontIcon>> Icons = new();

    /// <summary>Color de la cuenta activa (el coral de la marca hasta que haya una).</summary>
    public static Color Current { get; private set; } = Palette.Rgb(0xD2, 0x69, 0x4A);

    public static FontIcon Create(string glyph, double size = 16)
    {
        var icon = new FontIcon { Glyph = glyph, FontSize = size, Foreground = new SolidColorBrush(Current) };
        Icons.Add(new WeakReference<FontIcon>(icon));
        return icon;
    }

    public static void Apply(Color accent)
    {
        Current = accent;
        Icons.RemoveAll(w => !w.TryGetTarget(out _));
        foreach (var w in Icons)
            if (w.TryGetTarget(out var icon)) icon.Foreground = new SolidColorBrush(accent);
        SetSystemAccent(accent);
    }

    /// <summary>
    /// Interruptores, casillas y botones principales de los diálogos usan el acento del sistema: se le pisa el color
    /// con el de la cuenta. Los controles que ya existen lo toman al recrearse; los nuevos, de entrada.
    /// </summary>
    private static void SetSystemAccent(Color accent)
    {
        try
        {
            var res = Microsoft.UI.Xaml.Application.Current.Resources;
            var on = Palette.TextOnColor(accent);
            res["SystemAccentColor"] = accent;
            res["AccentFillColorDefaultBrush"] = new SolidColorBrush(accent);
            res["AccentFillColorSecondaryBrush"] = new SolidColorBrush(Palette.WithAlpha(accent, 0xE6));
            res["AccentFillColorTertiaryBrush"] = new SolidColorBrush(Palette.WithAlpha(accent, 0xCC));
            res["TextOnAccentFillColorPrimaryBrush"] = new SolidColorBrush(on);
            res["TextOnAccentFillColorSecondaryBrush"] = new SolidColorBrush(Palette.WithAlpha(on, 0xCC));
        }
        catch (Exception)
        {
            // Si los recursos no aceptan el cambio, los íconos igual quedan del color de la cuenta.
        }
    }
}
