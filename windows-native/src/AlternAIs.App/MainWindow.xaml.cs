using AlternAIs.App.Platform;
using AlternAIs.App.Ui;
using AlternAIs.App.Web;
using AlternAIs.Application;
using AlternAIs.Domain;
using AlternAIs.Domain.Models;
using AlternAIs.Domain.Policies;
using AlternAIs.Domain.UseCases;
using AlternAIs.Infrastructure;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Controls.Primitives;
using Microsoft.UI.Xaml.Input;
using Microsoft.UI.Xaml.Media;
using Windows.Foundation;
using Windows.System;
using Shape = Microsoft.UI.Xaml.Shapes;

namespace AlternAIs.App;

/// <summary>
/// Ventana principal: barra de cuentas (izquierda, derecha o abajo), la cuenta activa en su propio WebView2
/// y un cajón lateral (ajustes, mensajes guardados o navegador). Toda la lógica de negocio está en
/// <see cref="AccountsService"/> y el dominio; acá solo se dibuja y se conectan los botones.
/// </summary>
public sealed partial class MainWindow : Window
{
    private enum SidePanel { None, Settings, Snippets, Browser }

    private const double RailSize = 76;

    private readonly IClock _clock = new SystemClock();
    private readonly AccountsService _svc;
    private readonly Grid _webHost = new();
    private readonly AccountWebViews _views;
    private readonly AutoSender _sender;
    private readonly DispatcherTimer _timer = new() { Interval = TimeSpan.FromSeconds(30) };

    private readonly Border _rail = new();
    private readonly StackPanel _railTop = new();
    private readonly StackPanel _railAccounts = new();
    private readonly ScrollViewer _railScroll = new();
    private readonly StackPanel _railBottom = new();
    private readonly Border _panelHost = new();
    private readonly TextBlock _empty = new();
    private readonly Dictionary<AiService, Button> _serviceButtons = new();

    private SidePanel _panel = SidePanel.None;
    private AppState _rendered;

    public MainWindow()
    {
        InitializeComponent();
        Title = "AlternAIs";
        try
        {
            AppWindow.SetIcon(Path.Combine(AppContext.BaseDirectory, "Assets", "icon.ico"));
            AppWindow.Resize(new Windows.Graphics.SizeInt32(1280, 820));
        }
        catch (Exception)
        {
        }

        _svc = new AccountsService(new JsonFileStateStore(JsonFileStateStore.DefaultPath()), _clock);
        _rendered = _svc.State;
        _views = new AccountWebViews(_webHost);
        _sender = new AutoSender(_views);
        AccountWebViews.PurgeOrphans(_svc.State.Claude.Accounts.Concat(_svc.State.Replit.Accounts).Select(a => a.ProfileName));
        _views.UrlChanged += (id, url) => _svc.RememberUrl(id, url);
        _svc.Changed += OnChanged;

        BuildChrome();
        BuildLayout();
        AddShortcuts();
        Root.Loaded += async (_, _) =>
        {
            Dialogs.Root = Root.XamlRoot;
            Platform.Startup.Set(_svc.State.Settings.LaunchAtLogin);
            ApplyTheme();
            RefreshRail();
            await ShowSelectedAsync();
            await OnTickAsync();
            await ShowWelcomeAsync();
        };
        _timer.Tick += async (_, _) => await OnTickAsync();
        _timer.Start();
    }

    // ---------------------------------------------------------------- Bienvenida

    /// <summary>Guía corta de la primera vez (se muestra una sola vez y se puede saltear).</summary>
    private async Task ShowWelcomeAsync()
    {
        if (_svc.State.TutorialShown) return;
        _svc.MarkTutorialShown();
        await Dialogs.Message("Bienvenido a AlternAIs",
            "• Tocá «+» para agregar una cuenta y poné el correo con el que iniciás sesión (nunca la contraseña).\n" +
            "• Cada cuenta tiene su sesión aislada: tocá su círculo para cambiar.\n" +
            "• Clic derecho sobre un círculo: editar, marcar límite de uso, cerrar sesión o eliminar.\n" +
            "• Arriba de la barra elegís Claude o Replit; cada uno tiene sus propias cuentas.\n" +
            "• Abajo: mensajes guardados, navegador y ajustes.\n\n" +
            "Las cuentas son para usos distintos y respetan sus propios límites.");
    }

    // ---------------------------------------------------------------- Estado

    private static AiService ServiceOfState(AppState s) => s.Service;

    private Palette CurrentPalette =>
        Palette.For(_svc.State.Service == AiService.Replit ? _svc.State.ReplitTheme : _svc.State.ClaudeTheme);

    private void OnChanged(AppState s)
    {
        var prev = _rendered;
        _rendered = s;
        var layoutChanged = prev.Settings.RailPos != s.Settings.RailPos;
        var themeChanged = prev.ClaudeTheme != s.ClaudeTheme || prev.ReplitTheme != s.ReplitTheme || prev.Service != s.Service;
        var accountsChanged = !ReferenceEquals(prev.Claude, s.Claude) || !ReferenceEquals(prev.Replit, s.Replit);
        if (layoutChanged) BuildLayout();
        if (themeChanged || layoutChanged) ApplyTheme();
        if (accountsChanged || themeChanged || layoutChanged) RefreshRail();
        if (prev.Service != ServiceOfState(s) || prev.Current.SelectedId != s.Current.SelectedId || accountsChanged)
            _ = ShowSelectedAsync();
    }

    private async Task ShowSelectedAsync()
    {
        var acc = _svc.Selected;
        if (acc is null)
        {
            _views.ShowOnly(null);
            _empty.Text = "Todavía no hay cuentas en " + _svc.State.Service.Label() + ".\nTocá «+» para agregar la primera.";
            _empty.Visibility = Visibility.Visible;
            return;
        }
        _empty.Visibility = Visibility.Collapsed;
        try
        {
            await _views.GetAsync(acc, _svc.State.Service);
        }
        catch (Exception ex)
        {
            _empty.Text = "No se pudo abrir el navegador integrado (WebView2).\nInstalá «Microsoft Edge WebView2 Runtime» y volvé a abrir la app.\n\n" + ex.Message;
            _empty.Visibility = Visibility.Visible;
            return;
        }
        if (_svc.SelectedId == acc.Id) _views.ShowOnly(acc.Id);
    }

    private void ApplyTheme()
    {
        var pal = CurrentPalette;
        Root.RequestedTheme = pal.ElementTheme;
        Root.Background = Palette.Brush(pal.Surface);
        Dialogs.Theme = pal.ElementTheme;
        _empty.Foreground = Palette.Brush(pal.Muted);
        _rail.Background = Palette.Brush(pal.Stripe);
        _panelHost.Background = Palette.Brush(pal.Surface);
    }

    /// <summary>Cada 30 s: termina los descansos vencidos, avisa y manda el mensaje automático si había.</summary>
    private async Task OnTickAsync()
    {
        foreach (var a in _svc.ExpireLimits())
        {
            var hasAuto = a.AutoReply.Length > 0;
            Notifier.Show("«" + a.Name + "» ya puede usarse",
                hasAuto ? "Terminó el descanso. Enviando el mensaje automático…" : "Terminó el descanso por límite de uso.");
            if (hasAuto) await AutoReplyAsync(a);
        }
        RefreshRail();
    }

    private async Task AutoReplyAsync(Account account)
    {
        var service = _svc.ServiceOf(account.Id);
        if (service is null) return;
        var text = account.AutoReply;
        _svc.SetAutoReply(account.Id, ""); // se limpia antes de enviar: nunca se manda dos veces
        var ok = await _sender.SendAsync(account, service.Value, text);
        if (ok)
        {
            Notifier.Show("Mensaje enviado a «" + account.Name + "»", "«" + Shorten(text) + "»");
        }
        else
        {
            CopyToClipboard(text);
            Notifier.Show("No se pudo enviar el mensaje a «" + account.Name + "»",
                "Copié el mensaje: abrí la cuenta y pegalo. «" + Shorten(text) + "»");
        }
    }

    private static string Shorten(string t) => t.Length > 80 ? t[..80] + "…" : t;

    private static void CopyToClipboard(string text)
    {
        try
        {
            var pkg = new Windows.ApplicationModel.DataTransfer.DataPackage();
            pkg.SetText(text);
            Windows.ApplicationModel.DataTransfer.Clipboard.SetContent(pkg);
        }
        catch (Exception)
        {
        }
    }

    // ---------------------------------------------------------------- Armado de la ventana

    private void BuildChrome()
    {
        _empty.HorizontalAlignment = HorizontalAlignment.Center;
        _empty.VerticalAlignment = VerticalAlignment.Center;
        _empty.TextAlignment = TextAlignment.Center;
        _empty.TextWrapping = TextWrapping.Wrap;
        _empty.FontSize = 16;
        _empty.Margin = new Thickness(24);

        _railScroll.Content = _railAccounts;
        _railScroll.VerticalScrollBarVisibility = ScrollBarVisibility.Hidden;
        _railScroll.HorizontalScrollBarVisibility = ScrollBarVisibility.Hidden;

        foreach (var service in Enum.GetValues<AiService>())
        {
            var s = service;
            var b = new Button
            {
                Content = s.Label(), FontSize = 11, Padding = new Thickness(4, 6, 4, 6), Margin = new Thickness(4, 2, 4, 2),
                MinWidth = 60, HorizontalAlignment = HorizontalAlignment.Center
            };
            b.Click += (_, _) => _svc.SwitchService(s);
            _serviceButtons[s] = b;
            _railTop.Children.Add(b);
        }

        _railBottom.Children.Add(RailButton("", "Mensajes guardados (Ctrl+M)", () => TogglePanel(SidePanel.Snippets)));
        _railBottom.Children.Add(RailButton("", "Navegador (Ctrl+B)", () => TogglePanel(SidePanel.Browser)));
        _railBottom.Children.Add(RailButton("", "Ajustes (Ctrl+,)", () => TogglePanel(SidePanel.Settings)));

        _rail.Child = new Grid { Children = { _railTop, _railScroll, _railBottom } };
        _panelHost.Visibility = Visibility.Collapsed;
    }

    private static Button RailButton(string glyph, string tip, Action click)
    {
        var b = new Button
        {
            Content = new FontIcon { Glyph = glyph, FontSize = 18 },
            Width = 48, Height = 44, Margin = new Thickness(4), Background = new SolidColorBrush(Microsoft.UI.Colors.Transparent),
            BorderThickness = new Thickness(0), HorizontalAlignment = HorizontalAlignment.Center
        };
        ToolTipService.SetToolTip(b, tip);
        Microsoft.UI.Xaml.Automation.AutomationProperties.SetName(b, tip);
        b.Click += (_, _) => click();
        return b;
    }

    /// <summary>Acomoda la barra según la posición elegida (izquierda, derecha o abajo).</summary>
    private void BuildLayout()
    {
        Root.Children.Clear();
        Root.RowDefinitions.Clear();
        Root.ColumnDefinitions.Clear();

        var content = new Grid();
        content.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        content.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        _webHost.Children.Remove(_empty);
        _webHost.Children.Add(_empty);
        if (_webHost.Parent is Panel old) old.Children.Remove(_webHost);
        if (_panelHost.Parent is Panel oldPanel) oldPanel.Children.Remove(_panelHost);
        Grid.SetColumn(_webHost, 0);
        Grid.SetColumn(_panelHost, 1);
        content.Children.Add(_webHost);
        content.Children.Add(_panelHost);

        var pos = _svc.State.Settings.RailPos;
        var vertical = pos != "bottom";
        var rg = (Grid)_rail.Child;
        rg.RowDefinitions.Clear();
        rg.ColumnDefinitions.Clear();
        if (vertical)
        {
            rg.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
            rg.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
            rg.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
            Grid.SetRow(_railTop, 0); Grid.SetRow(_railScroll, 1); Grid.SetRow(_railBottom, 2);
            Grid.SetColumn(_railTop, 0); Grid.SetColumn(_railScroll, 0); Grid.SetColumn(_railBottom, 0);
        }
        else
        {
            rg.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            rg.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            rg.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            Grid.SetColumn(_railTop, 0); Grid.SetColumn(_railScroll, 1); Grid.SetColumn(_railBottom, 2);
            Grid.SetRow(_railTop, 0); Grid.SetRow(_railScroll, 0); Grid.SetRow(_railBottom, 0);
        }
        var orientation = vertical ? Orientation.Vertical : Orientation.Horizontal;
        _railTop.Orientation = orientation;
        _railAccounts.Orientation = orientation;
        _railBottom.Orientation = orientation;
        _railScroll.VerticalScrollMode = vertical ? ScrollMode.Auto : ScrollMode.Disabled;
        _railScroll.HorizontalScrollMode = vertical ? ScrollMode.Disabled : ScrollMode.Auto;
        _railScroll.VerticalScrollBarVisibility = vertical ? ScrollBarVisibility.Hidden : ScrollBarVisibility.Disabled;
        _railScroll.HorizontalScrollBarVisibility = vertical ? ScrollBarVisibility.Disabled : ScrollBarVisibility.Hidden;

        switch (pos)
        {
            case "right":
                Root.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
                Root.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(RailSize) });
                Grid.SetColumn(content, 0); Grid.SetColumn(_rail, 1);
                break;
            case "bottom":
                Root.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
                Root.RowDefinitions.Add(new RowDefinition { Height = new GridLength(RailSize) });
                Grid.SetRow(content, 0); Grid.SetRow(_rail, 1);
                break;
            default:
                Root.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(RailSize) });
                Root.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
                Grid.SetColumn(_rail, 0); Grid.SetColumn(content, 1);
                break;
        }
        Root.Children.Add(content);
        Root.Children.Add(_rail);
    }

    private void AddShortcuts()
    {
        void Add(VirtualKey key, VirtualKeyModifiers mod, Action action)
        {
            var acc = new KeyboardAccelerator { Key = key, Modifiers = mod };
            acc.Invoked += (_, e) => { action(); e.Handled = true; };
            Root.KeyboardAccelerators.Add(acc);
        }
        Add(VirtualKey.Tab, VirtualKeyModifiers.Control, () => _svc.Step(+1));
        Add(VirtualKey.Tab, VirtualKeyModifiers.Control | VirtualKeyModifiers.Shift, () => _svc.Step(-1));
        Add(VirtualKey.B, VirtualKeyModifiers.Control, () => TogglePanel(SidePanel.Browser));
        Add(VirtualKey.M, VirtualKeyModifiers.Control, () => TogglePanel(SidePanel.Snippets));
        Add((VirtualKey)188, VirtualKeyModifiers.Control, () => TogglePanel(SidePanel.Settings)); // Ctrl+,
        Add(VirtualKey.N, VirtualKeyModifiers.Control, () => _ = AddAccountAsync());
    }

    // ---------------------------------------------------------------- Barra de cuentas

    private void RefreshRail()
    {
        var pal = CurrentPalette;
        var now = _clock.NowMs;
        var vertical = _svc.State.Settings.RailPos != "bottom";

        foreach (var (service, button) in _serviceButtons)
        {
            var on = service == _svc.State.Service;
            button.Background = on ? Palette.Brush(Palette.Rgb(0xE0, 0x7A, 0x5F)) : new SolidColorBrush(Microsoft.UI.Colors.Transparent);
            button.Foreground = on ? Palette.Brush(Microsoft.UI.Colors.White) : Palette.Brush(pal.OnSurface);
        }

        _railAccounts.Children.Clear();
        foreach (var a in _svc.Accounts)
            _railAccounts.Children.Add(AccountItem(a, a.Id == _svc.SelectedId, pal, now, vertical));

        var add = RailButton("", "Agregar cuenta (Ctrl+N)", () => _ = AddAccountAsync());
        add.Foreground = Palette.Brush(pal.OnSurface);
        _railAccounts.Children.Add(add);
    }

    private static string StatusLine(Account a, long now)
    {
        var status = a.IsResting(now) ? "faltan " + RemainingTime.Format(a.LimitUntil - now) : "Disponible";
        var extra = string.Join(" · ", new[] { a.Purpose, a.Email }.Where(x => x.Length > 0));
        return a.Name + " · " + status + (extra.Length > 0 ? "\n" + extra : "");
    }

    private UIElement AccountItem(Account a, bool selected, Palette pal, long now, bool vertical)
    {
        var resting = a.IsResting(now);
        var grid = new Grid { Width = 56, Height = 56 };
        grid.Children.Add(new Shape.Ellipse
        {
            Width = 40, Height = 40, Fill = Palette.Brush(Palette.FromArgb(a.Color)), Opacity = resting ? 0.55 : 1,
            HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center
        });
        grid.Children.Add(new TextBlock
        {
            Text = AccountInitial.Of(a.Name), FontSize = 17, FontWeight = Microsoft.UI.Text.FontWeights.Bold,
            Foreground = Palette.Brush(Palette.TextOn(a.Color)),
            HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center
        });
        if (resting) grid.Children.Add(Ring(Math.Max(a.LimitFraction(now), 0.02f), 52, Palette.FromArgb(a.Color)));
        if (selected)
        {
            grid.Children.Add(new Border
            {
                Background = Palette.Brush(pal.OnSurface), CornerRadius = new CornerRadius(2),
                Width = vertical ? 4 : 24, Height = vertical ? 24 : 4,
                HorizontalAlignment = vertical ? HorizontalAlignment.Left : HorizontalAlignment.Center,
                VerticalAlignment = vertical ? VerticalAlignment.Center : VerticalAlignment.Bottom
            });
        }

        var id = a.Id;
        var btn = new Button
        {
            Content = grid, Padding = new Thickness(0), BorderThickness = new Thickness(0),
            Background = new SolidColorBrush(Microsoft.UI.Colors.Transparent), Margin = new Thickness(2)
        };
        ToolTipService.SetToolTip(btn, StatusLine(a, now));
        Microsoft.UI.Xaml.Automation.AutomationProperties.SetName(btn, a.Name);
        btn.Click += (_, _) => _svc.Select(id);
        btn.ContextFlyout = AccountMenu(id, resting);
        return btn;
    }

    /// <summary>Anillo que se va borrando en sentido horario a medida que pasa el descanso.</summary>
    private static UIElement Ring(float fraction, double size, Windows.UI.Color color)
    {
        var r = size / 2 - 2;
        var c = size / 2;
        UIElement result;
        if (fraction >= 0.999f)
        {
            result = new Shape.Ellipse
            {
                Width = size - 4, Height = size - 4, Stroke = Palette.Brush(color), StrokeThickness = 3,
                HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center
            };
        }
        else
        {
            var angle = 2 * Math.PI * fraction;
            var figure = new PathFigure { StartPoint = new Point(c, c - r), IsClosed = false };
            figure.Segments.Add(new ArcSegment
            {
                Point = new Point(c + r * Math.Sin(angle), c - r * Math.Cos(angle)),
                Size = new Size(r, r), SweepDirection = SweepDirection.Clockwise, IsLargeArc = fraction > 0.5f
            });
            var geometry = new PathGeometry();
            geometry.Figures.Add(figure);
            result = new Shape.Path
            {
                Data = geometry, Stroke = Palette.Brush(color), StrokeThickness = 3, Width = size, Height = size,
                StrokeStartLineCap = PenLineCap.Round, StrokeEndLineCap = PenLineCap.Round,
                HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center
            };
        }
        result.IsHitTestVisible = false;
        return result;
    }

    private MenuFlyout AccountMenu(string id, bool resting)
    {
        var menu = new MenuFlyout();
        void Item(string text, Func<Task> action)
        {
            var item = new MenuFlyoutItem { Text = text };
            item.Click += async (_, _) => await action();
            menu.Items.Add(item);
        }
        Item("Editar nombre, propósito o color", () => EditAccountAsync(id));
        Item("Recargar", () => { _views.Reload(id); return Task.CompletedTask; });
        Item("Marcar límite de uso…", () => MarkLimitAsync(id));
        if (resting) Item("Quitar marca de límite", () => { _svc.ClearLimit(id); return Task.CompletedTask; });
        menu.Items.Add(new MenuFlyoutSeparator());
        Item("Cerrar sesión", () => SignOutAsync(id));
        Item("Eliminar cuenta", () => DeleteAccountAsync(id));
        return menu;
    }

    // ---------------------------------------------------------------- Acciones sobre cuentas

    private Account? Find(string id) => _svc.Accounts.FirstOrDefault(a => a.Id == id);

    private async Task AddAccountAsync()
    {
        var form = await Dialogs.AccountForm(null, AccountPalette.DefaultFor(_svc.Accounts.Count));
        if (form is null) return;
        if (_svc.Add(form) is null)
            await Dialogs.Message("Límite de cuentas", "Ya hay " + PlanImport.MaxPerService + " cuentas en este servicio.");
    }

    private async Task EditAccountAsync(string id)
    {
        var acc = Find(id);
        if (acc is null) return;
        var form = await Dialogs.AccountForm(acc, acc.Color);
        if (form is not null) _svc.Edit(id, form);
    }

    private async Task MarkLimitAsync(string id)
    {
        var acc = Find(id);
        if (acc is null) return;
        var until = await Dialogs.PickLimit(_svc.State.Service, _clock);
        if (until is null) return;
        _svc.MarkLimit(id, until.Value);
        var auto = await Dialogs.Text("Mensaje automático (opcional)",
            Find(id)?.AutoReply ?? "", "Guardar", multiline: true);
        if (!string.IsNullOrWhiteSpace(auto)) _svc.SetAutoReply(id, auto);
        // Salta a otra cuenta libre si hay (como el aviso de Android).
        var other = _svc.FirstAvailableExceptCurrent();
        if (other is not null && _svc.SelectedId == id &&
            await Dialogs.Confirm("Cuenta en descanso", "¿Pasar a «" + other.Name + "», que está disponible?", "Pasar"))
            _svc.Select(other.Id);
    }

    private async Task SignOutAsync(string id)
    {
        var acc = Find(id);
        if (acc is null) return;
        if (!await Dialogs.Confirm("Cerrar sesión",
                "Se cierra la sesión de «" + acc.Name + "» en este equipo. Vas a tener que volver a iniciar sesión.", "Cerrar sesión")) return;
        try { await _views.SignOutAsync(acc, _svc.State.Service); }
        catch (Exception ex) { await Dialogs.Message("No se pudo cerrar la sesión", ex.Message); }
    }

    private async Task DeleteAccountAsync(string id)
    {
        var acc = Find(id);
        if (acc is null) return;
        if (!await Dialogs.Confirm("Eliminar cuenta",
                "Se elimina «" + acc.Name + "» y su sesión de este equipo. No se puede deshacer.", "Eliminar")) return;
        _svc.Remove(id);
        await _views.DeleteAsync(acc);
    }
}
