using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Windows.UI;

namespace AlternAIs.App.Ui;

/// <summary>
/// Fila de pestañas del navegador y del explorador: una píldora por pestaña (la activa con el color de la cuenta),
/// una × para cerrarla (solo si hay más de una) y un «+» para abrir otra. Se vuelve a dibujar con <see cref="Render"/>.
/// </summary>
internal sealed class TabBar
{
    private readonly StackPanel _strip = new() { Orientation = Orientation.Horizontal, Spacing = 6 };

    public ScrollViewer View { get; }

    public TabBar()
    {
        View = new ScrollViewer
        {
            Content = _strip,
            HorizontalScrollBarVisibility = ScrollBarVisibility.Auto,
            VerticalScrollBarVisibility = ScrollBarVisibility.Disabled,
            HorizontalScrollMode = ScrollMode.Auto,
            Margin = new Thickness(10, 6, 10, 2)
        };
    }

    public void Render(
        IReadOnlyList<(int Id, string Title)> tabs, int activeId, Color fg, Color accent,
        Action<int> select, Action<int> close, Action add)
    {
        _strip.Children.Clear();
        var clear = new SolidColorBrush(Microsoft.UI.Colors.Transparent);
        foreach (var (id, title) in tabs)
        {
            var tabId = id;
            var on = id == activeId;
            var row = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 6 };
            row.Children.Add(new TextBlock
            {
                Text = title, MaxWidth = 150, TextTrimming = TextTrimming.CharacterEllipsis,
                VerticalAlignment = VerticalAlignment.Center, FontSize = 12,
                FontWeight = on ? Microsoft.UI.Text.FontWeights.SemiBold : Microsoft.UI.Text.FontWeights.Normal,
                Foreground = new SolidColorBrush(fg)
            });
            if (tabs.Count > 1)
            {
                var x = new Border { Padding = new Thickness(4), CornerRadius = new CornerRadius(10), Background = clear, Child = AccentIcons.Create("\uE711", 10) };
                x.Tapped += (_, e) => { e.Handled = true; close(tabId); };
                ToolTipService.SetToolTip(x, "Cerrar pestaña");
                row.Children.Add(x);
            }
            var tab = new Border
            {
                Padding = new Thickness(12, 4, 8, 4), CornerRadius = new CornerRadius(14),
                BorderThickness = new Thickness(on ? 1.5 : 1),
                BorderBrush = new SolidColorBrush(on ? accent : Palette.WithAlpha(fg, 0x33)),
                Background = new SolidColorBrush(Palette.WithAlpha(on ? accent : fg, (byte)(on ? 0x2E : 0x10))),
                Child = row
            };
            tab.Tapped += (_, _) => select(tabId);
            ToolTipService.SetToolTip(tab, title);
            _strip.Children.Add(tab);
        }
        var plus = new Button { Content = AccentIcons.Create("\uE710", 12), Padding = new Thickness(8, 4, 8, 4), Background = clear, BorderThickness = new Thickness(0) };
        ToolTipService.SetToolTip(plus, "Pestaña nueva");
        plus.Click += (_, _) => add();
        _strip.Children.Add(plus);
    }
}
