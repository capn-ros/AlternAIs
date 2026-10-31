
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Media;
using Windows.UI;

namespace AlternAIs.App.Ui;

/// <summary>Colores de la interfaz según el tema de cada servicio (Claude oscuro, Replit claro por defecto).</summary>
internal sealed record Palette(Color Surface, Color OnSurface, Color Muted, Color Stripe, bool Dark)
{
    public static Palette For(string theme) => theme == "light"
        ? new Palette(Rgb(0xFA, 0xF9, 0xF5), Rgb(0x1F, 0x1E, 0x1D), Rgb(0x6B, 0x68, 0x62), Rgb(0xEC, 0xE9, 0xE2), false)
        : new Palette(Rgb(0x26, 0x26, 0x24), Rgb(0xEC, 0xE9, 0xE4), Rgb(0xA8, 0xA4, 0x9C), Rgb(0x1F, 0x1E, 0x1D), true);

    public ElementTheme ElementTheme => Dark ? ElementTheme.Dark : ElementTheme.Light;

    public static Color Rgb(byte r, byte g, byte b) => Color.FromArgb(255, r, g, b);

    /// <summary>De un color ARGB (int, como lo guarda el dominio) a Color.</summary>
    public static Color FromArgb(int argb) => Color.FromArgb(
        (byte)((argb >> 24) & 0xFF), (byte)((argb >> 16) & 0xFF), (byte)((argb >> 8) & 0xFF), (byte)(argb & 0xFF));

    public static SolidColorBrush Brush(Color c) => new(c);

    /// <summary>Negro o blanco, el que se lea mejor sobre [argb].</summary>
    public static Color TextOn(int argb) => TextOnColor(FromArgb(argb));

    public static Color TextOnColor(Color c)
    {
        var lum = (0.299 * c.R + 0.587 * c.G + 0.114 * c.B) / 255.0;
        return lum > 0.6 ? Microsoft.UI.Colors.Black : Microsoft.UI.Colors.White;
    }

    /// <summary>El mismo color con otra opacidad (0–255).</summary>
    public static Color WithAlpha(Color c, byte alpha) => Color.FromArgb(alpha, c.R, c.G, c.B);
}
