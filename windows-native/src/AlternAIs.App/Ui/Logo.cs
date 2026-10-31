using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Windows.Foundation;
using Shape = Microsoft.UI.Xaml.Shapes;

namespace AlternAIs.App.Ui;

/// <summary>
/// Logo de AlternAIs (mismo dibujo que Android e index.html de Electron): fondo carbón redondeado y un círculo
/// partido en dos mitades desfasadas, coral (Claude) abajo a la izquierda y crema (Replit) arriba a la derecha.
/// </summary>
internal static class Logo
{
    public static readonly Windows.UI.Color Charcoal = Palette.Rgb(0x1C, 0x19, 0x17);
    public static readonly Windows.UI.Color Coral = Palette.Rgb(0xD9, 0x77, 0x57);
    public static readonly Windows.UI.Color Cream = Palette.Rgb(0xF5, 0xEF, 0xE6);

    public static FrameworkElement Create(double size)
    {
        var canvas = new Canvas { Width = 120, Height = 120 };
        canvas.Children.Add(new Shape.Rectangle { Width = 120, Height = 120, RadiusX = 28, RadiusY = 28, Fill = new SolidColorBrush(Charcoal) });
        canvas.Children.Add(Half(left: true));
        canvas.Children.Add(Half(left: false));
        return new Viewbox { Width = size, Height = size, Child = canvas, IsHitTestVisible = false };
    }

    private static Shape.Path Half(bool left)
    {
        var figure = new PathFigure { StartPoint = new Point(60, 28), IsClosed = true, IsFilled = true };
        figure.Segments.Add(new ArcSegment
        {
            Point = new Point(60, 92),
            Size = new Size(32, 32),
            SweepDirection = left ? SweepDirection.Counterclockwise : SweepDirection.Clockwise
        });
        var geometry = new PathGeometry();
        geometry.Figures.Add(figure);
        return new Shape.Path
        {
            Data = geometry,
            Fill = new SolidColorBrush(left ? Coral : Cream),
            RenderTransform = new TranslateTransform { X = left ? -1 : 1, Y = left ? 5 : -5 }
        };
    }
}
