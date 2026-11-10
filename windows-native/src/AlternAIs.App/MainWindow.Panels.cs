using System.Diagnostics;
using AlternAIs.App.Platform;
using AlternAIs.App.Ui;
using AlternAIs.App.Web;
using AlternAIs.Application;
using AlternAIs.Domain.Models;
using AlternAIs.Domain.UseCases;
using AlternAIs.Infrastructure;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Microsoft.Web.WebView2.Core;
using Windows.Storage;
using Windows.Storage.Pickers;
using WinRT.Interop;

namespace AlternAIs.App;

/// <summary>Cajones laterales: ajustes, mensajes guardados y navegador interno (el de archivos está en MainWindow.Explorer.cs).</summary>
public sealed partial class MainWindow
{
    private UIElement? _browserPanel;
    private WebView2? _browserView;

    private void TogglePanel(SidePanel kind)
    {
        _panel = _panel == kind ? SidePanel.None : kind;
        RenderPanel();
    }

    private void RenderPanel()
    {
        if (_panel == SidePanel.None)
        {
            _panelHost.Visibility = Visibility.Collapsed;
            _panelHost.Child = null;
            return;
        }
        var pal = CurrentPalette;
        _panelHost.Width = _panel switch { SidePanel.Browser => 620, SidePanel.Files => 540, _ => 400 };
        _panelHost.Background = Palette.Brush(pal.Surface);
        _panelHost.BorderBrush = Palette.Brush(pal.Stripe);
        _panelHost.BorderThickness = new Thickness(1, 0, 0, 0);
        _panelHost.Child = null; // un elemento solo puede tener un padre
        _panelHost.Child = _panel switch
        {
            SidePanel.Settings => BuildSettings(pal),
            SidePanel.Snippets => BuildSnippets(pal),
            SidePanel.Files => EnsureExplorer(pal),
            _ => EnsureBrowser(pal)
        };
        _panelHost.Visibility = Visibility.Visible;
        if (_panel == SidePanel.Files) RefreshExplorer();
    }

    private static TextBlock Heading(string text) => new()
    {
        Text = text, FontSize = 20, FontWeight = Microsoft.UI.Text.FontWeights.SemiBold, Margin = new Thickness(0, 0, 0, 12)
    };

    private Button Small(string text, Action click)
    {
        var b = new Button { Content = text, Padding = new Thickness(10, 4, 10, 4), Margin = new Thickness(0, 0, 6, 6), FontSize = 12 };
        b.Click += (_, _) => click();
        return b;
    }

    // ---------------------------------------------------------------- Ajustes

    private UIElement BuildSettings(Palette pal)
    {
        var s = _svc.State;
        var service = s.Service;
        var stack = new StackPanel { Margin = new Thickness(20), Spacing = 14 };
        // Como en Android y Electron: tocar el logo alterna oscuro/claro del servicio activo.
        var logo = new Button
        {
            Content = Logo.Create(40), Padding = new Thickness(0), BorderThickness = new Thickness(0),
            Background = new SolidColorBrush(Microsoft.UI.Colors.Transparent), CornerRadius = new CornerRadius(10)
        };
        ToolTipService.SetToolTip(logo, "Cambiar a tema " + (s.ClaudeTheme == "dark" ? "claro" : "oscuro"));
        logo.Click += (_, _) =>
        {
            var current = _svc.State.ClaudeTheme;
            _svc.SetTheme(service, current == "dark" ? "light" : "dark");
            RenderPanel();
        };
        var header = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 12 };
        header.Children.Add(logo);
        var titles = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
        titles.Children.Add(new TextBlock { Text = "AlternAIs", FontSize = 20, FontWeight = Microsoft.UI.Text.FontWeights.SemiBold });
        titles.Children.Add(new TextBlock { Text = "Ajustes · " + service.Label(), Opacity = 0.7 });
        header.Children.Add(titles);
        stack.Children.Add(header);

        var dark = new ToggleSwitch
        {
            Header = "Tema de " + service.Label(), OnContent = "Oscuro", OffContent = "Claro",
            IsOn = s.ClaudeTheme == "dark"
        };
        dark.Toggled += (_, _) => _svc.SetTheme(service, dark.IsOn ? "dark" : "light");
        stack.Children.Add(dark);

        var rail = new ComboBox
        {
            Header = "Posición de la barra de cuentas", ItemsSource = new[] { "Izquierda", "Derecha", "Abajo" },
            SelectedIndex = s.Settings.RailPos switch { "right" => 1, "bottom" => 2, _ => 0 }
        };
        rail.SelectionChanged += (_, _) =>
        {
            var pos = rail.SelectedIndex switch { 1 => "right", 2 => "bottom", _ => "left" };
            if (pos != _svc.State.Settings.RailPos) _svc.SetSettings(_svc.State.Settings with { RailPos = pos });
        };
        stack.Children.Add(rail);

        var login = new ToggleSwitch { Header = "Iniciar con Windows", IsOn = s.Settings.LaunchAtLogin };
        login.Toggled += (_, _) =>
        {
            Startup.Set(login.IsOn);
            _svc.SetSettings(_svc.State.Settings with { LaunchAtLogin = login.IsOn });
        };
        stack.Children.Add(login);

        var close = new ComboBox
        {
            Header = "Al cerrar la ventana", ItemsSource = new[] { "Cerrar AlternAIs", "Seguir en la bandeja del sistema" },
            SelectedIndex = s.Settings.CloseToTray ? 1 : 0
        };
        close.SelectionChanged += (_, _) =>
        {
            var tray = close.SelectedIndex == 1;
            if (tray != _svc.State.Settings.CloseToTray) _svc.SetSettings(_svc.State.Settings with { CloseToTray = tray });
        };
        stack.Children.Add(close);
        stack.Children.Add(new TextBlock
        {
            Text = "En la bandeja siguen los avisos de fin de descanso y el envío automático. Clic derecho en el ícono: cuentas y «Salir».",
            TextWrapping = TextWrapping.Wrap, Opacity = 0.7, FontSize = 12, Margin = new Thickness(0, -8, 0, 0)
        });

        var dl = new ComboBox
        {
            Header = "Descargas del navegador", ItemsSource = new[] { "Carpeta Descargas", "Preguntar cada vez", "Carpeta fija" },
            SelectedIndex = s.Browser.DlMode switch { "ask" => 1, "folder" => 2, _ => 0 }
        };
        dl.SelectionChanged += (_, _) =>
        {
            var mode = dl.SelectedIndex switch { 1 => "ask", 2 => "folder", _ => "default" };
            if (mode != _svc.State.Browser.DlMode) _svc.SetBrowser(_svc.State.Browser with { DlMode = mode });
            if (mode == "folder" && _svc.State.Browser.DlDir.Length == 0) _ = PickDownloadFolderAsync();
        };
        stack.Children.Add(dl);
        stack.Children.Add(Small("Elegir carpeta fija…", () => _ = PickDownloadFolderAsync()));

        stack.Children.Add(new TextBlock { Text = "Cuentas", FontWeight = Microsoft.UI.Text.FontWeights.SemiBold, Margin = new Thickness(0, 8, 0, 0) });
        var row = new StackPanel { Orientation = Orientation.Horizontal };
        row.Children.Add(Small("Exportar cuentas", () => _ = ExportAsync()));
        row.Children.Add(Small("Importar cuentas", () => _ = ImportAsync()));
        stack.Children.Add(row);
        stack.Children.Add(Small("Abrir la carpeta Descargas", OpenDownloads));
        stack.Children.Add(Small("Ver el tutorial otra vez", () => { TogglePanel(SidePanel.Settings); StartTutorial(); }));

        stack.Children.Add(new TextBlock { Text = "Acerca de", FontWeight = Microsoft.UI.Text.FontWeights.SemiBold, Margin = new Thickness(0, 8, 0, 0) });
        var version = typeof(MainWindow).Assembly.GetName().Version?.ToString(3) ?? "1.0.0";
        stack.Children.Add(new TextBlock { Text = "AlternAIs " + version + " · Windows nativo (WinUI 3)", Opacity = 0.8 });
        stack.Children.Add(new TextBlock
        {
            Text = "Uso responsable: las cuentas son para usos distintos y respetan sus propios límites. " +
                   "Usar varias cuentas para esquivar los límites de una sola va contra los términos de uso de Anthropic. " +
                   "AlternAIs no guarda contraseñas.",
            TextWrapping = TextWrapping.Wrap, Opacity = 0.8
        });
        return new ScrollViewer { Content = stack };
    }

    private async Task PickDownloadFolderAsync()
    {
        var picker = new FolderPicker { SuggestedStartLocation = PickerLocationId.Downloads };
        picker.FileTypeFilter.Add("*");
        InitializeWithWindow.Initialize(picker, WindowNative.GetWindowHandle(this));
        var folder = await picker.PickSingleFolderAsync();
        if (folder is null) return;
        _svc.SetBrowser(_svc.State.Browser with { DlMode = "folder", DlDir = folder.Path });
    }

    private static void OpenDownloads()
    {
        try
        {
            var path = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "Downloads");
            Process.Start(new ProcessStartInfo { FileName = path, UseShellExecute = true });
        }
        catch (Exception)
        {
        }
    }

    private async Task ExportAsync()
    {
        var count = ExportJson.CountAccounts(_svc.State);
        if (count == 0)
        {
            await Dialogs.Message("Exportar cuentas", "Todavía no hay cuentas para exportar.");
            return;
        }
        var picker = new FileSavePicker { SuggestedFileName = "alternais-cuentas", SuggestedStartLocation = PickerLocationId.DocumentsLibrary };
        picker.FileTypeChoices.Add("Archivo JSON", new List<string> { ".json" });
        InitializeWithWindow.Initialize(picker, WindowNative.GetWindowHandle(this));
        var file = await picker.PickSaveFileAsync();
        if (file is null) return;
        try
        {
            await FileIO.WriteTextAsync(file, ExportJson.Build(_svc.State, _clock.NowMs));
            await Dialogs.Message("Exportar cuentas",
                "Se guardaron " + count + " cuentas. El archivo no lleva contraseñas ni sesiones: al importarlas hay que volver a iniciar sesión.");
        }
        catch (Exception)
        {
            await Dialogs.Message("Exportar cuentas", "No se pudo guardar el archivo.");
        }
    }

    private async Task ImportAsync()
    {
        var picker = new FileOpenPicker { SuggestedStartLocation = PickerLocationId.DocumentsLibrary };
        picker.FileTypeFilter.Add(".json");
        InitializeWithWindow.Initialize(picker, WindowNative.GetWindowHandle(this));
        var file = await picker.PickSingleFileAsync();
        if (file is null) return;
        string text;
        try { text = await FileIO.ReadTextAsync(file); }
        catch (Exception)
        {
            await Dialogs.Message("Importar cuentas", "No se pudo leer el archivo.");
            return;
        }
        var parsed = ExportJson.Parse(text);
        if (parsed is null)
        {
            await Dialogs.Message("Importar cuentas", "El archivo no es válido.");
            return;
        }
        var r = _svc.Import(parsed);
        await Dialogs.Message("Importar cuentas",
            "Se agregaron " + r.Added + " cuentas" + (r.Skipped > 0 ? " (" + r.Skipped + " omitidas por repetidas o vacías)" : "") +
            ". Iniciá sesión en cada una.");
    }

    // ---------------------------------------------------------------- Mensajes guardados

    private UIElement BuildSnippets(Palette pal)
    {
        var list = _svc.State.Snippets;
        var stack = new StackPanel { Margin = new Thickness(20), Spacing = 6 };
        stack.Children.Add(Heading("Mensajes guardados"));
        var actions = new StackPanel { Orientation = Orientation.Horizontal };
        actions.Children.Add(Small("Agregar mensaje", () => _ = AddSnippetAsync()));
        actions.Children.Add(Small("Texto propio…", () => _ = OwnTextAsync()));
        stack.Children.Add(actions);
        stack.Children.Add(new TextBlock
        {
            Text = "«Escribir» lo deja en la caja del chat de la cuenta activa; «Enviar» además lo manda.",
            TextWrapping = TextWrapping.Wrap, Opacity = 0.7, FontSize = 12
        });
        if (list.Count == 0)
            stack.Children.Add(new TextBlock { Text = "Todavía no hay mensajes. Agregá el primero.", Opacity = 0.7 });

        for (var i = 0; i < list.Count; i++)
        {
            var index = i;
            var text = list[i];
            var card = new StackPanel { Spacing = 4, Margin = new Thickness(0, 0, 0, 10) };
            card.Children.Add(new TextBlock { Text = text, TextWrapping = TextWrapping.Wrap, MaxLines = 6, TextTrimming = TextTrimming.CharacterEllipsis });
            var row1 = new StackPanel { Orientation = Orientation.Horizontal };
            row1.Children.Add(Small("Escribir en el chat", () => _ = WriteSnippetAsync(text, send: false)));
            row1.Children.Add(Small("Enviar", () => _ = WriteSnippetAsync(text, send: true)));
            row1.Children.Add(Small("Copiar", () => CopyToClipboard(text)));
            var row2 = new StackPanel { Orientation = Orientation.Horizontal };
            if (index > 0) row2.Children.Add(Small("Subir", () => { _svc.SetSnippets(Snippets.MoveUp(_svc.State.Snippets, index)); RenderPanel(); }));
            row2.Children.Add(Small("Editar", () => _ = EditSnippetAsync(index)));
            row2.Children.Add(Small("Borrar", () => { _svc.SetSnippets(Snippets.Remove(_svc.State.Snippets, index)); RenderPanel(); }));
            card.Children.Add(row1);
            card.Children.Add(row2);
            stack.Children.Add(card);
        }
        return new ScrollViewer { Content = stack };
    }

    private async Task AddSnippetAsync()
    {
        var text = await Dialogs.Text("Nuevo mensaje", "", "Guardar", multiline: true);
        if (text is null) return;
        _svc.SetSnippets(Snippets.Add(_svc.State.Snippets, text));
        RenderPanel();
    }

    /// <summary>Escribir algo sin guardarlo (o guardarlo y escribirlo), como en Android.</summary>
    private async Task OwnTextAsync()
    {
        var box = new TextBox
        {
            AcceptsReturn = true, TextWrapping = TextWrapping.Wrap, MinHeight = 120, MaxLength = Snippets.MaxLength,
            PlaceholderText = "Escribí el mensaje"
        };
        var d = new ContentDialog
        {
            Title = "Texto propio", Content = box, PrimaryButtonText = "Escribir en el chat", SecondaryButtonText = "Guardar y escribir",
            CloseButtonText = "Cancelar", DefaultButton = ContentDialogButton.Primary, RequestedTheme = Dialogs.Theme
        };
        d.Opened += (_, _) => box.Focus(FocusState.Programmatic);
        var result = await Dialogs.Show(d);
        var text = box.Text;
        if (result == ContentDialogResult.None || string.IsNullOrWhiteSpace(text)) return;
        if (result == ContentDialogResult.Secondary)
        {
            _svc.SetSnippets(Snippets.Add(_svc.State.Snippets, text));
            RenderPanel();
        }
        await WriteSnippetAsync(text, send: false);
    }

    private async Task EditSnippetAsync(int index)
    {
        var current = _svc.State.Snippets;
        if (index < 0 || index >= current.Count) return;
        var text = await Dialogs.Text("Editar mensaje", current[index], "Guardar", multiline: true);
        if (text is null) return;
        _svc.SetSnippets(Snippets.Replace(_svc.State.Snippets, index, text));
        RenderPanel();
    }

    private async Task WriteSnippetAsync(string text, bool send)
    {
        var acc = _svc.Selected;
        if (acc is null)
        {
            await Dialogs.Message("Mensajes guardados", "Elegí una cuenta primero.");
            return;
        }
        try
        {
            var wv = await _views.GetAsync(acc, _svc.State.Service);
            var result = await wv.CoreWebView2.ExecuteScriptAsync(AutoSendScript.Build(text, send));
            if (!AutoSendScript.IsOk(result))
            {
                CopyToClipboard(text);
                await Dialogs.Message("Mensajes guardados", "No encontré la caja de texto del chat. Copié el mensaje: pegalo con Ctrl+V.");
            }
        }
        catch (Exception ex)
        {
            await Dialogs.Message("Mensajes guardados", ex.Message);
        }
    }

    // ---------------------------------------------------------------- Navegador

    private UIElement EnsureBrowser(Palette pal)
    {
        if (_browserPanel is not null) return _browserPanel;

        var back = new Button { Content = AccentIcons.Create("", 14), Width = 40 };
        var fwd = new Button { Content = AccentIcons.Create("", 14), Width = 40, Margin = new Thickness(4, 0, 0, 0) };
        var reload = new Button { Content = AccentIcons.Create("", 14), Width = 40, Margin = new Thickness(4, 0, 0, 0) };
        var url = new TextBox { PlaceholderText = "Buscá o escribí una dirección", Margin = new Thickness(8, 0, 8, 0), VerticalAlignment = VerticalAlignment.Center };
        var close = new Button { Content = AccentIcons.Create("", 14), Width = 40 };
        var wv = new WebView2();
        _browserView = wv;

        var bar = new Grid { Margin = new Thickness(10, 10, 10, 4) };
        bar.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        bar.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        bar.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        bar.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        bar.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        Grid.SetColumn(back, 0); Grid.SetColumn(fwd, 1); Grid.SetColumn(reload, 2); Grid.SetColumn(url, 3); Grid.SetColumn(close, 4);
        foreach (var e in new UIElement[] { back, fwd, reload, url, close }) bar.Children.Add(e);

        var quick = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(10, 0, 10, 4) };
        foreach (var (label, link) in BrowserRules.QuickLinks)
        {
            var target = link;
            quick.Children.Add(Small(label, () => wv.CoreWebView2?.Navigate(target)));
        }
        quick.Children.Add(Small("Abrir en el navegador del sistema", () =>
        {
            var current = wv.CoreWebView2?.Source;
            if (!BrowserRules.IsRememberable(current)) return;
            try { Process.Start(new ProcessStartInfo { FileName = current!, UseShellExecute = true }); } catch (Exception) { }
        }));
        var quickScroll = new ScrollViewer { Content = quick, HorizontalScrollBarVisibility = ScrollBarVisibility.Auto, VerticalScrollBarVisibility = ScrollBarVisibility.Disabled, HorizontalScrollMode = ScrollMode.Auto };

        var grid = new Grid();
        grid.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        grid.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        grid.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
        Grid.SetRow(bar, 0); Grid.SetRow(quickScroll, 1); Grid.SetRow(wv, 2);
        grid.Children.Add(bar); grid.Children.Add(quickScroll); grid.Children.Add(wv);

        back.Click += (_, _) => { if (wv.CanGoBack) wv.GoBack(); };
        fwd.Click += (_, _) => { if (wv.CanGoForward) wv.GoForward(); };
        reload.Click += (_, _) => wv.Reload();
        close.Click += (_, _) => TogglePanel(SidePanel.Browser);
        void Go()
        {
            switch (BrowserRules.Resolve(url.Text, Uri.EscapeDataString))
            {
                case BrowserLoad load: wv.CoreWebView2?.Navigate(load.Url); wv.Focus(FocusState.Programmatic); break;
                case BrowserReject reject: _ = Dialogs.Message("Navegador", reject.Message); break;
            }
        }
        url.KeyDown += (_, e) => { if (e.Key == Windows.System.VirtualKey.Enter) Go(); };

        _ = InitBrowserAsync(wv, url);
        _browserPanel = grid;
        return grid;
    }

    /// <summary>El navegador tiene su propia sesión (carpeta «browser»), separada de todas las cuentas.</summary>
    private async Task InitBrowserAsync(WebView2 wv, TextBox url)
    {
        try
        {
            var env = await CoreWebView2Environment.CreateWithOptionsAsync(null, Path.Combine(AccountWebViews.ProfilesDir, "browser"), new CoreWebView2EnvironmentOptions());
            await wv.EnsureCoreWebView2Async(env);
            wv.CoreWebView2.SourceChanged += (_, _) =>
            {
                var current = wv.CoreWebView2.Source;
                if (url.FocusState == FocusState.Unfocused) url.Text = current;
                if (BrowserRules.IsRememberable(current) && current != _svc.State.Browser.LastUrl)
                    _svc.SetBrowser(_svc.State.Browser with { LastUrl = current.Length > 2000 ? current[..2000] : current });
            };
            wv.CoreWebView2.NewWindowRequested += (_, e) => { e.Handled = true; wv.CoreWebView2.Navigate(e.Uri); };
            await AccountWebViews.AttachShortcuts(wv.CoreWebView2, combo => RunShortcut(Shortcuts.FromCombo(combo)));
            wv.CoreWebView2.DownloadStarting += (_, e) =>
            {
                var b = _svc.State.Browser;
                if (b.DlMode == "folder" && Directory.Exists(b.DlDir))
                {
                    var name = Path.GetFileName(e.ResultFilePath);
                    e.ResultFilePath = Path.Combine(b.DlDir, name);
                    e.Handled = true; // sin diálogo: se guarda directo en la carpeta elegida
                }
                else if (b.DlMode == "ask")
                {
                    e.Handled = false; // WebView2 muestra su diálogo para elegir dónde
                }
            };
            wv.CoreWebView2.Navigate(BrowserRules.StartUrl(_svc.State.Browser.LastUrl));
        }
        catch (Exception ex)
        {
            await Dialogs.Message("Navegador", "No se pudo iniciar el navegador integrado: " + ex.Message);
        }
    }
}
