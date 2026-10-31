using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Windows.Foundation;
using Windows.UI;

namespace AlternAIs.App.Ui;

/// <summary>
/// Barra de tiempo de la cuenta activa (como la barra plegada de Android y la barra superior de Electron):
/// pastilla translúcida, relleno del color de la cuenta = lo que falta del descanso, y el texto
/// «Trabajo · faltan 2 h 10 min» que cambia de color donde está el relleno.
/// </summary>
internal sealed class TimeBar
{
    /// <summary>El elemento a poner en pantalla (se usa composición: no se hereda de controles de WinUI).</summary>
    public Grid View { get; } = new();

    private readonly Border _track = new();
    private readonly Border _fill = new() { HorizontalAlignment = HorizontalAlignment.Left };
    private readonly TextBlock _text = new();
    private readonly TextBlock _textOnFill = new();
    private readonly Grid _fillLayer = new();
    private double _fraction;

    public TimeBar(double width, double height)
    {
        View.Width = width;
        View.Height = height;
        View.IsHitTestVisible = false;
        var radius = new CornerRadius(height / 2);
        _track.CornerRadius = radius;
        _fill.CornerRadius = radius;
        foreach (var t in new[] { _text, _textOnFill })
        {
            t.FontSize = Math.Round(height * 0.5);
            t.HorizontalAlignment = HorizontalAlignment.Center;
            t.VerticalAlignment = VerticalAlignment.Center;
            t.TextTrimming = TextTrimming.CharacterEllipsis;
            t.MaxWidth = width - height;
            t.FontWeight = Microsoft.UI.Text.FontWeights.SemiBold;
        }
        _fillLayer.Children.Add(_textOnFill);
        View.Children.Add(_track);
        View.Children.Add(_fill);
        View.Children.Add(_text);
        View.Children.Add(_fillLayer);
        View.SizeChanged += (_, _) => Layout();
    }

    /// <param name="fraction">0 = disponible (sin relleno), 1 = recién marcado.</param>
    public void Set(double fraction, string label, Color accent, Color onSurface)
    {
        _fraction = Math.Clamp(fraction, 0, 1);
        _text.Text = label;
        _textOnFill.Text = label;
        _track.Background = new SolidColorBrush(Color.FromArgb(0x26, onSurface.R, onSurface.G, onSurface.B));
        _fill.Background = new SolidColorBrush(accent);
        _text.Foreground = new SolidColorBrush(onSurface);
        _textOnFill.Foreground = new SolidColorBrush(Palette.TextOnColor(accent));
        Layout();
    }

    private void Layout()
    {
        var w = View.ActualWidth > 0 ? View.ActualWidth : View.Width;
        var h = View.ActualHeight > 0 ? View.ActualHeight : View.Height;
        if (double.IsNaN(w) || double.IsNaN(h)) return;
        var fillWidth = _fraction <= 0 ? 0 : Math.Max(h, w * _fraction);
        _fill.Width = fillWidth;
        _fill.Visibility = fillWidth > 0 ? Visibility.Visible : Visibility.Collapsed;
        // El texto «sobre el relleno» se recorta al ancho del relleno.
        _fillLayer.Clip = new RectangleGeometry { Rect = new Rect(0, 0, fillWidth, h) };
        _fillLayer.Visibility = _fill.Visibility;
    }
}
