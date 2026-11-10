using AlternAIs.App.Platform;
using AlternAIs.App.Ui;
using AlternAIs.App.Web;
using AlternAIs.Application;
using AlternAIs.Domain;
using AlternAIs.Domain.Models;
using AlternAIs.Domain.Policies;
using AlternAIs.Domain.UseCases;
using AlternAIs.Infrastructure;
using Microsoft.UI.Windowing;
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
/// Ventana principal: barra de título propia (logo + barra de tiempo de la cuenta activa), barra de cuentas
/// (izquierda, derecha o abajo), la cuenta activa en su propio WebView2 y un cajón lateral (ajustes, archivos,
/// mensajes guardados o navegador). Toda la lógica de negocio está en <see cref="AccountsService"/> y el
/// dominio; acá solo se dibuja y se conectan los botones. Otras partes: MainWindow.Panels.cs (cajones),
/// MainWindow.Explorer.cs (archivos), MainWindow.Shell.cs (bandeja, avisos, logo animado, tutorial).
/// </summary>
public sealed partial class MainWindow : Window
{
    private enum SidePanel { None, Settings, Snippets, Browser, Files }

    private const double RailSize = 76;
    private const double TitleBarHeight = 36;
    private static readonly Windows.UI.Color Brand = Palette.Rgb(0xD9, 0x77, 0x57);

    private readonly IClock _clock = new SystemClock();
    private readonly AccountsService _svc;
    private readonly Grid _main = new();
    private readonly Grid _webHost = new();
    private readonly AccountWebViews _views;
    private readonly AutoSender _sender;
    private readonly DispatcherTimer _timer = new() { Interval = TimeSpan.FromSeconds(30) };

    private readonly Border _titleBar = new();
    private readonly TextBlock _titleText = new();
    private readonly TimeBar _timeBar = new(300, 22);
    private readonly Border _rail = new();
    private readonly StackPanel _railTop = new();
    private readonly StackPanel _railAccounts = new();
    private readonly ScrollViewer _railScroll = new();
    private readonly StackPanel _railBottom = new();
    private readonly Border _panelHost = new();
    private readonly TextBlock _empty = new();
    private Button? _addButton;

    private SidePanel _panel = SidePanel.None;
    private AppState _rendered;

    public MainWindow(NotificationAction? launchedFrom = null)
    {
        InitializeComponent();
        Title = SingleInstance.WindowTitle;
        _pendingNotification = launchedFrom;
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
        AccountWebViews.PurgeOrphans(_svc.State.Claude.Accounts.Select(a => a.ProfileName));
        _views.UrlChanged += (id, url) => _svc.RememberUrl(id, url);
        _views.ShortcutPressed += combo => RunShortcut(Shortcuts.FromCombo(combo));
        _views.LoadingChanged += UpdateLoading;
        _svc.Changed += OnChanged;

        BuildChrome();
        BuildTitleBar();
        BuildLayout();
        AddShortcuts();
        SetupShell();
        var startHidden = Startup.LaunchedHidden && _svc.State.Settings.CloseToTray;
        if (!startHidden) ShowSplash();

        Root.Loaded += async (_, _) =>
        {
            Dialogs.Root = Root.XamlRoot;
            Platform.Startup.Set(_svc.State.Settings.LaunchAtLogin);
            ApplyTheme();
            RefreshRail();
            if (startHidden) HideToTray();
            await ShowSelectedAsync();
            await OnTickAsync();
            await HandlePendingNotificationAsync();
        };
        _timer.Tick += async (_, _) => await OnTickAsync();
        _timer.Start();
    }

    // ---------------------------------------------------------------- Estado

    private Palette CurrentPalette =>
        Palette.For(_svc.State.ClaudeTheme);

    /// <summary>Color de la cuenta activa (el «chrome» de la ventana lo usa, como en Android); sin cuentas, el coral.</summary>
    private Windows.UI.Color Accent => _svc.Selected is { } a ? Palette.FromArgb(a.Color) : Brand;

    private void OnChanged(AppState s)
    {
        var prev = _rendered;
        _rendered = s;
        var layoutChanged = prev.Settings.RailPos != s.Settings.RailPos;
        var themeChanged = prev.ClaudeTheme != s.ClaudeTheme;
        var accountsChanged = !ReferenceEquals(prev.Claude, s.Claude);
        if (layoutChanged) BuildLayout();
        if (themeChanged || layoutChanged || accountsChanged) ApplyTheme();
        if (accountsChanged || themeChanged || layoutChanged) RefreshRail();
        if (prev.Current.SelectedId != s.Current.SelectedId || accountsChanged)
            _ = ShowSelectedAsync();
        if (prev.Settings.CloseToTray != s.Settings.CloseToTray || accountsChanged) UpdateTray();
    }

    private async Task ShowSelectedAsync()
    {
        var acc = _svc.Selected;
        RefreshTitle();
        if (acc is null)
        {
            _views.ShowOnly(null);
            _empty.Text = "Todavía no hay cuentas en " + _svc.State.Service.Label() + ".\nTocá «+» para agregar la primera.";
            _empty.Visibility = Visibility.Visible;
            UpdateLoading();
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
            UpdateLoading();
            return;
        }
        if (_svc.SelectedId == acc.Id) _views.ShowOnly(acc.Id);
        UpdateLoading();
    }

    private void ApplyTheme()
    {
        var pal = CurrentPalette;
        var accent = Accent;
        Root.RequestedTheme = pal.ElementTheme;
        Root.Background = Palette.Brush(pal.Surface);
        Dialogs.Theme = pal.ElementTheme;
        _views.SetDark(pal.Dark);
        UpdateLoadingTheme(pal.Dark);
        _empty.Foreground = Palette.Brush(pal.Muted);
        _rail.Background = Palette.Brush(pal.Stripe);
        _rail.BorderBrush = Palette.Brush(accent);
        _rail.BorderThickness = _svc.State.Settings.RailPos switch
        {
            "right" => new Thickness(1, 0, 0, 0),
            "bottom" => new Thickness(0, 1, 0, 0),
            _ => new Thickness(0, 0, 1, 0)
        };
        _panelHost.Background = Palette.Brush(pal.Surface);
        _titleBar.Background = Palette.Brush(pal.Stripe);
        _titleBar.BorderBrush = Palette.Brush(accent);
        _titleText.Foreground = Palette.Brush(pal.OnSurface);
        ApplyCaptionColors(pal);
        RefreshTitle();
    }

    /// <summary>Botones de minimizar/maximizar/cerrar del color del tema (Windows 11; en 10 puede no aplicarse).</summary>
    private void ApplyCaptionColors(Palette pal)
    {
        try
        {
            if (!AppWindowTitleBar.IsCustomizationSupported()) return;
            var bar = AppWindow.TitleBar;
            bar.ButtonBackgroundColor = Microsoft.UI.Colors.Transparent;
            bar.ButtonInactiveBackgroundColor = Microsoft.UI.Colors.Transparent;
            bar.ButtonForegroundColor = pal.OnSurface;
            bar.ButtonInactiveForegroundColor = pal.Muted;
            bar.ButtonHoverBackgroundColor = Palette.WithAlpha(pal.OnSurface, 0x22);
            bar.ButtonHoverForegroundColor = pal.OnSurface;
            bar.ButtonPressedBackgroundColor = Palette.WithAlpha(pal.OnSurface, 0x38);
            bar.ButtonPressedForegroundColor = pal.OnSurface;
        }
        catch (Exception)
        {
        }
    }

    /// <summary>Barra de tiempo de la cuenta activa en la barra de título.</summary>
    private void RefreshTitle()
    {
        var acc = _svc.Selected;
        if (acc is null)
        {
            _timeBar.View.Visibility = Visibility.Collapsed;
            return;
        }
        var now = _clock.NowMs;
        _timeBar.View.Visibility = Visibility.Visible;
        _timeBar.Set(acc.LimitFraction(now), AccountStatusText.TimeBarText(acc, now), Palette.FromArgb(acc.Color), CurrentPalette.OnSurface);
        ToolTipService.SetToolTip(_timeBar.View, AccountStatusText.InfoLine(acc, now, _clock.Zone, System.Globalization.CultureInfo.CurrentCulture));
    }

    /// <summary>Cada 30 s: termina los descansos vencidos, avisa y manda el mensaje automático si había.</summary>
    private async Task OnTickAsync()
    {
        foreach (var a in _svc.ExpireLimits())
        {
            var hasAuto = a.AutoReply.Length > 0;
            if (hasAuto)
            {
                Notifier.Show(LimitNotificationText.DoneTitle(a.Name), LimitNotificationText.DoneText(true), a.Id);
                await AutoReplyAsync(a);
            }
            else
            {
                Notifier.ShowWithReply(LimitNotificationText.DoneTitle(a.Name), LimitNotificationText.DoneText(false), a.Id,
                    LimitNotificationText.SnippetActions(_svc.State.Snippets));
            }
        }
        RefreshRail();
        RefreshTitle();
        UpdateTray();
    }

    private async Task AutoReplyAsync(Account account)
    {
        var text = account.AutoReply;
        _svc.SetAutoReply(account.Id, ""); // se limpia antes de enviar: nunca se manda dos veces
        await SendToAccountAsync(account.Id, text);
    }

    /// <summary>Escribe y envía <paramref name="text"/> en la cuenta (aunque la ventana esté en la bandeja) y avisa cómo salió.</summary>
    private async Task SendToAccountAsync(string accountId, string text)
    {
        var service = _svc.ServiceOf(accountId);
        var account = service is null ? null : _svc.State.Bucket(service.Value).Accounts.FirstOrDefault(a => a.Id == accountId);
        if (account is null || service is null) return;
        bool ok;
        try { ok = await _sender.SendAsync(account, service.Value, text); }
        catch (Exception) { ok = false; }
        if (!ok) CopyToClipboard(text);
        Notifier.Show(LimitNotificationText.ResultTitle(account.Name, ok), LimitNotificationText.ResultText(text, ok), account.Id);
    }

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
        _webHost.Children.Add(_loadingLayer);
        _webHost.Children.Add(_empty);

        _railScroll.Content = _railAccounts;
        _railScroll.VerticalScrollBarVisibility = ScrollBarVisibility.Hidden;
        _railScroll.HorizontalScrollBarVisibility = ScrollBarVisibility.Hidden;

        _railTop.Margin = new Thickness(0, 6, 0, 6);

        _railBottom.Children.Add(RailButton("", "Navegador (Ctrl+B)", () => TogglePanel(SidePanel.Browser)));
        _railBottom.Children.Add(RailButton("", "Archivos (Ctrl+E)", () => TogglePanel(SidePanel.Files)));
        _railBottom.Children.Add(RailButton("", "Mensajes guardados (Ctrl+M)", () => TogglePanel(SidePanel.Snippets)));
        _railBottom.Children.Add(RailButton("", "Ajustes (Ctrl+,)", () => TogglePanel(SidePanel.Settings)));

        _rail.Child = new Grid { Children = { _railTop, _railScroll, _railBottom } };
        _panelHost.Visibility = Visibility.Collapsed;
    }

    /// <summary>Barra de título propia: logo y nombre a la izquierda, la barra de tiempo en el medio.</summary>
    private void BuildTitleBar()
    {
        var grid = new Grid { Height = TitleBarHeight };
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(140) }); // botones de la ventana

        var brand = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8, Margin = new Thickness(12, 0, 12, 0), VerticalAlignment = VerticalAlignment.Center };
        brand.Children.Add(Logo.Create(20));
        _titleText.Text = "AlternAIs";
        _titleText.FontWeight = Microsoft.UI.Text.FontWeights.SemiBold;
        _titleText.FontSize = 13;
        _titleText.VerticalAlignment = VerticalAlignment.Center;
        brand.Children.Add(_titleText);
        Grid.SetColumn(brand, 0);
        grid.Children.Add(brand);

        _timeBar.View.HorizontalAlignment = HorizontalAlignment.Center;
        _timeBar.View.VerticalAlignment = VerticalAlignment.Center;
        Grid.SetColumn(_timeBar.View, 1);
        grid.Children.Add(_timeBar.View);

        _titleBar.Child = grid;
        _titleBar.BorderThickness = new Thickness(0, 0, 0, 1);

        Root.RowDefinitions.Clear();
        Root.RowDefinitions.Add(new RowDefinition { Height = new GridLength(TitleBarHeight) });
        Root.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
        Grid.SetRow(_titleBar, 0);
        Grid.SetRow(_main, 1);
        Root.Children.Add(_titleBar);
        Root.Children.Add(_main);
        try
        {
            ExtendsContentIntoTitleBar = true;
            SetTitleBar(_titleBar);
        }
        catch (Exception)
        {
            // Sin barra propia: queda la de Windows arriba y la nuestra igual muestra la barra de tiempo.
        }
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
        _main.Children.Clear();
        _main.RowDefinitions.Clear();
        _main.ColumnDefinitions.Clear();

        var content = new Grid();
        content.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        content.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
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
                _main.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
                _main.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(RailSize) });
                Grid.SetColumn(content, 0); Grid.SetColumn(_rail, 1);
                break;
            case "bottom":
                _main.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
                _main.RowDefinitions.Add(new RowDefinition { Height = new GridLength(RailSize) });
                Grid.SetRow(content, 0); Grid.SetRow(_rail, 1);
                break;
            default:
                _main.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(RailSize) });
                _main.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
                Grid.SetColumn(_rail, 0); Grid.SetColumn(content, 1);
                break;
        }
        _main.Children.Add(content);
        _main.Children.Add(_rail);
    }

    // ---------------------------------------------------------------- Atajos

    private void AddShortcuts()
    {
        void Add(VirtualKey key, VirtualKeyModifiers mod, Shortcut shortcut)
        {
            var acc = new KeyboardAccelerator { Key = key, Modifiers = mod };
            acc.Invoked += (_, e) => { RunShortcut(shortcut); e.Handled = true; };
            Root.KeyboardAccelerators.Add(acc);
        }
        Add(VirtualKey.Tab, VirtualKeyModifiers.Control, new Shortcut(ShortcutAction.NextAccount));
        Add(VirtualKey.Tab, VirtualKeyModifiers.Control | VirtualKeyModifiers.Shift, new Shortcut(ShortcutAction.PreviousAccount));
        Add(VirtualKey.B, VirtualKeyModifiers.Control, new Shortcut(ShortcutAction.ToggleBrowser));
        Add(VirtualKey.M, VirtualKeyModifiers.Control, new Shortcut(ShortcutAction.ToggleSnippets));
        Add(VirtualKey.E, VirtualKeyModifiers.Control, new Shortcut(ShortcutAction.ToggleFiles));
        Add((VirtualKey)188, VirtualKeyModifiers.Control, new Shortcut(ShortcutAction.ToggleSettings)); // Ctrl+,
        Add(VirtualKey.N, VirtualKeyModifiers.Control, new Shortcut(ShortcutAction.AddAccount));
        for (var i = 0; i < 9; i++)
            Add(VirtualKey.Number1 + i, VirtualKeyModifiers.Control, new Shortcut(ShortcutAction.SelectAccount, i));
        Add(VirtualKey.Escape, VirtualKeyModifiers.None, new Shortcut(ShortcutAction.ClosePanel));
    }

    private void RunShortcut(Shortcut? s)
    {
        if (s is null) return;
        switch (s.Action)
        {
            case ShortcutAction.NextAccount: _svc.Step(+1); break;
            case ShortcutAction.PreviousAccount: _svc.Step(-1); break;
            case ShortcutAction.SelectAccount:
                if (s.Index < _svc.Accounts.Count) _svc.Select(_svc.Accounts[s.Index].Id);
                break;
            case ShortcutAction.AddAccount: _ = AddAccountAsync(); break;
            case ShortcutAction.ToggleBrowser: TogglePanel(SidePanel.Browser); break;
            case ShortcutAction.ToggleSnippets: TogglePanel(SidePanel.Snippets); break;
            case ShortcutAction.ToggleFiles: TogglePanel(SidePanel.Files); break;
            case ShortcutAction.ToggleSettings: TogglePanel(SidePanel.Settings); break;
            case ShortcutAction.ClosePanel:
                if (_panel != SidePanel.None) TogglePanel(_panel);
                break;
        }
    }

    // ---------------------------------------------------------------- Barra de cuentas

    private void RefreshRail()
    {
        var pal = CurrentPalette;
        var now = _clock.NowMs;
        var vertical = _svc.State.Settings.RailPos != "bottom";
        var accent = Accent;

        foreach (var child in _railBottom.Children)
        {
            if (child is Button { Content: FontIcon icon }) icon.Foreground = Palette.Brush(accent);
        }

        _railAccounts.Children.Clear();
        foreach (var a in _svc.Accounts)
            _railAccounts.Children.Add(AccountItem(a, a.Id == _svc.SelectedId, pal, now, vertical));

        _addButton = RailButton("", "Agregar cuenta (Ctrl+N)", () => _ = AddAccountAsync());
        ((FontIcon)_addButton.Content).Foreground = Palette.Brush(pal.OnSurface);
        _railAccounts.Children.Add(_addButton);
    }

    private UIElement AccountItem(Account a, bool selected, Palette pal, long now, bool vertical)
    {
        var resting = a.IsResting(now);
        var color = Palette.FromArgb(a.Color);
        var grid = new Grid { Width = 56, Height = 56 };
        if (selected)
        {
            // Luz sutil detrás de la cuenta activa (como Electron).
            grid.Children.Add(new Shape.Ellipse
            {
                Width = 52, Height = 52, Fill = Palette.Brush(Palette.WithAlpha(color, 0x40)),
                HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center
            });
        }
        grid.Children.Add(new Shape.Ellipse
        {
            Width = 40, Height = 40, Fill = Palette.Brush(color), Opacity = resting ? 0.55 : 1,
            HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center
        });
        grid.Children.Add(new TextBlock
        {
            Text = AccountInitial.Of(a.Name), FontSize = 17, FontWeight = Microsoft.UI.Text.FontWeights.Bold,
            Foreground = Palette.Brush(Palette.TextOn(a.Color)),
            HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center
        });
        if (resting) grid.Children.Add(Ring(Math.Max(a.LimitFraction(now), 0.02f), 52, pal.Dark ? Microsoft.UI.Colors.White : Microsoft.UI.Colors.Black));
        if (selected)
        {
            grid.Children.Add(new Border
            {
                Background = Palette.Brush(color), CornerRadius = new CornerRadius(2),
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
        ToolTipService.SetToolTip(btn, AccountStatusText.Tooltip(a, now));
        Microsoft.UI.Xaml.Automation.AutomationProperties.SetName(btn, AccountStatusText.CircleDescription(a, now));
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
        void Item(string text, string glyph, Func<Task> action)
        {
            var item = new MenuFlyoutItem { Text = text, Icon = new FontIcon { Glyph = glyph } };
            item.Click += async (_, _) => await action();
            menu.Items.Add(item);
        }
        Item("Editar nombre, propósito o color", "", () => EditAccountAsync(id));
        Item("Recargar", "", () => { _views.Reload(id); return Task.CompletedTask; });
        Item("Marcar límite de uso…", "", () => MarkLimitAsync(id));
        if (resting) Item("Quitar marca de límite", "", () => { _svc.ClearLimit(id); return Task.CompletedTask; });
        menu.Items.Add(new MenuFlyoutSeparator());
        Item("Cerrar sesión", "", () => SignOutAsync(id));
        Item("Eliminar cuenta", "", () => DeleteAccountAsync(id));
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
        var auto = await Dialogs.AutoReply(_svc.State.Snippets, Find(id)?.AutoReply ?? "");
        if (auto is not null) _svc.SetAutoReply(id, auto);
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
