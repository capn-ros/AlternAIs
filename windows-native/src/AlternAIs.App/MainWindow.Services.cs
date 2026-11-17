using AlternAIs.App.Ui;
using AlternAIs.Domain.Models;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;

namespace AlternAIs.App;

/// <summary>Cambio rápido de IA: botón al comienzo de la barra de cuentas y fila de IAs en la pantalla «sin cuentas».</summary>
public sealed partial class MainWindow
{
    private readonly StackPanel _emptyServices = new()
    {
        Orientation = Orientation.Horizontal, Spacing = 8,
        HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center,
        Margin = new Thickness(24, 130, 24, 0), Visibility = Visibility.Collapsed
    };

    private string ServiceMenuText(AiService svc)
    {
        var n = _svc.State.Bucket(svc).Accounts.Count;
        return n > 0 ? $"{svc.Label()}  ({n})" : svc.Label();
    }

    /// <summary>Botón con la IA actual: un clic abre la lista para cambiar (sin pasar por Ajustes).</summary>
    private Button AiSwitchButton(Palette pal, Windows.UI.Color accent)
    {
        var current = _svc.State.Service;
        var btn = new Button
        {
            Content = new TextBlock
            {
                Text = current.Label(), FontSize = 10, FontWeight = Microsoft.UI.Text.FontWeights.SemiBold,
                TextTrimming = TextTrimming.CharacterEllipsis, HorizontalAlignment = HorizontalAlignment.Center,
                Foreground = Palette.Brush(pal.OnSurface)
            },
            Width = 52, Height = 30, Padding = new Thickness(2, 0, 2, 0), Margin = new Thickness(2, 4, 2, 6),
            CornerRadius = new CornerRadius(15), BorderThickness = new Thickness(1.5),
            BorderBrush = Palette.Brush(accent), Background = Palette.Brush(Palette.WithAlpha(accent, 0x2E))
        };
        ToolTipService.SetToolTip(btn, "IA: " + current.Label() + " — clic para cambiar de IA");
        Microsoft.UI.Xaml.Automation.AutomationProperties.SetName(btn, "IA actual: " + current.Label() + ". Cambiar de IA");
        var menu = new MenuFlyout();
        foreach (var svc in Enum.GetValues<AiService>())
        {
            var target = svc;
            var item = new ToggleMenuFlyoutItem { Text = ServiceMenuText(svc), IsChecked = svc == current };
            item.Click += (_, _) => _svc.SetService(target);
            menu.Items.Add(item);
        }
        btn.Flyout = menu;
        return btn;
    }

    /// <summary>Una píldora por IA bajo el mensaje «Todavía no hay cuentas»: se puede volver a Claude sin agregar nada.</summary>
    private void RenderEmptyServices(Palette pal)
    {
        _emptyServices.Children.Clear();
        var accent = Accent;
        foreach (var svc in Enum.GetValues<AiService>())
        {
            var target = svc;
            var on = svc == _svc.State.Service;
            var b = new Button
            {
                Content = new TextBlock { Text = svc.Label(), FontSize = 12, Foreground = Palette.Brush(pal.OnSurface) },
                Padding = new Thickness(12, 5, 12, 5), CornerRadius = new CornerRadius(16),
                BorderThickness = new Thickness(on ? 1.5 : 1),
                BorderBrush = Palette.Brush(on ? accent : Palette.WithAlpha(pal.OnSurface, 0x33)),
                Background = Palette.Brush(Palette.WithAlpha(on ? accent : pal.OnSurface, (byte)(on ? 0x2E : 0x10)))
            };
            b.Click += (_, _) => _svc.SetService(target);
            _emptyServices.Children.Add(b);
        }
    }
}
