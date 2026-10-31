using AlternAIs.App.Platform;
using AlternAIs.App.Ui;
using AlternAIs.App.Web;
using AlternAIs.Application;
using AlternAIs.Domain.Models;
using AlternAIs.Domain.UseCases;
using Microsoft.UI.Windowing;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using WinRT.Interop;

namespace AlternAIs.App;

/// <summary>
/// Lo que rodea a la ventana: ícono de la bandeja y «cerrar a la bandeja», avisos de Windows (responder desde el
/// aviso), logo animado de arranque y de carga, y el tutorial de la primera vez.
/// </summary>
public sealed partial class MainWindow
{
    private NotificationAction? _pendingNotification;
    private TrayIcon? _tray;
    private bool _quitting;

    private readonly Grid _loadingLayer = new() { Background = new SolidColorBrush(Logo.Charcoal), Visibility = Visibility.Collapsed };
    private LogoWebView? _loadingLogo;
    private long _loadingSince;
    private int _loadingShowToken;
    private int _loadingHideToken;

    private Grid? _splash;
    private LogoWebView? _splashLogo;

    private void SetupShell()
    {
        AppWindow.Closing += OnClosing;
        Notifier.Invoked += action => DispatcherQueue.TryEnqueue(() => _ = HandleNotificationAsync(action));
        UpdateTray();
    }

    // ---------------------------------------------------------------- Bandeja

    /// <summary>Con «Seguir en la bandeja», cerrar esconde la ventana: los avisos y el envío automático siguen.</summary>
    private void OnClosing(AppWindow sender, AppWindowClosingEventArgs args)
    {
        if (!_quitting && _svc.State.Settings.CloseToTray && EnsureTray())
        {
            args.Cancel = true;
            HideToTray();
            return;
        }
        _quitting = true;
        _tray?.Dispose();
        _tray = null;
    }

    private bool EnsureTray()
    {
        if (_tray is not null) return true;
        try
        {
            _tray = new TrayIcon(WindowNative.GetWindowHandle(this), Path.Combine(AppContext.BaseDirectory, "Assets", "icon.ico"));
            _tray.OpenRequested += ShowFromTray;
            _tray.QuitRequested += Quit;
            _tray.AccountRequested += entry =>
            {
                ShowFromTray();
                if (entry.AccountId is not null) OpenAccount(entry.AccountId);
            };
            return true;
        }
        catch (Exception)
        {
            _tray = null;
            return false;
        }
    }

    /// <summary>Pone, actualiza o saca el ícono según el ajuste (el menú lista las cuentas con su cuenta regresiva).</summary>
    private void UpdateTray()
    {
        if (!_svc.State.Settings.CloseToTray)
        {
            _tray?.Hide();
            return;
        }
        if (!EnsureTray()) return;
        try { _tray!.Show(TrayMenu.Build(_svc.State, _clock.NowMs)); } catch (Exception) { }
    }

    private void HideToTray()
    {
        UpdateTray();
        try { AppWindow.Hide(); } catch (Exception) { }
    }

    private void ShowFromTray()
    {
        try
        {
            AppWindow.Show();
            if (AppWindow.Presenter is OverlappedPresenter { State: OverlappedPresenterState.Minimized } p) p.Restore();
            Activate();
        }
        catch (Exception)
        {
        }
    }

    private void Quit()
    {
        _quitting = true;
        _tray?.Dispose();
        _tray = null;
        try { Close(); } catch (Exception) { }
        Microsoft.UI.Xaml.Application.Current.Exit();
    }

    /// <summary>Abre una cuenta de cualquiera de los dos servicios.</summary>
    private void OpenAccount(string id)
    {
        if (_svc.ServiceOf(id) is not { } service) return;
        _svc.SwitchService(service);
        _svc.Select(id);
    }

    // ---------------------------------------------------------------- Avisos

    private async Task HandlePendingNotificationAsync()
    {
        var pending = _pendingNotification;
        _pendingNotification = null;
        if (pending is not null) await HandleNotificationAsync(pending);
    }

    /// <summary>Tocar el aviso abre la cuenta; «Responder» o un mensaje guardado lo envían sin abrir la ventana.</summary>
    private async Task HandleNotificationAsync(NotificationAction action)
    {
        if (_svc.ServiceOf(action.AccountId) is null) return;
        if (action.Action is "reply" or "snippet")
        {
            var snippets = _svc.State.Snippets;
            var preset = action.Action == "snippet" && action.Slot >= 0 && action.Slot < snippets.Count ? snippets[action.Slot] : null;
            var text = LimitNotificationText.ReplyText(action.Action == "reply" ? action.Typed : null, preset);
            if (text is not null)
            {
                await SendToAccountAsync(action.AccountId, text);
                return;
            }
        }
        ShowFromTray();
        OpenAccount(action.AccountId);
    }

    // ---------------------------------------------------------------- Logo animado

    /// <summary>Animación de arranque (~1,7 s) encima de toda la ventana; tocarla la salta.</summary>
    private void ShowSplash()
    {
        _splash = new Grid { Background = new SolidColorBrush(Logo.Charcoal) };
        Grid.SetRowSpan(_splash, 2);
        _splashLogo = new LogoWebView();
        _splash.Children.Add(_splashLogo.View);
        _splash.Tapped += (_, _) => RemoveSplash();
        _splashLogo.Finished += RemoveSplash;
        Root.Children.Add(_splash);
        _ = StartSplashAsync();
    }

    private async Task StartSplashAsync()
    {
        // Tope de seguridad aparte: si WebView2 tarda o se cuelga, la ventana no queda tapada.
        DispatcherQueue.TryEnqueue(async () =>
        {
            await Task.Delay(5000);
            RemoveSplash();
        });
        var ok = _splashLogo is not null && await _splashLogo.StartAsync("intro.html");
        if (!ok) RemoveSplash();
    }

    private void RemoveSplash()
    {
        if (_splash is null) return;
        Root.Children.Remove(_splash);
        _splash = null;
        _splashLogo?.Close();
        _splashLogo = null;
        if (!_svc.State.TutorialShown) StartTutorial();
    }

    /// <summary>
    /// Logo en bucle mientras la cuenta activa carga por primera vez o se recarga: mínimo ~0,7 s y tope de 25 s.
    /// No aparece al cambiar entre cuentas ya cargadas.
    /// </summary>
    private async void UpdateLoading()
    {
        try
        {
            var id = _svc.SelectedId;
            var on = id is not null && _views.Has(id) && _views.IsLoading(id);
            if (on)
            {
                _loadingHideToken++;
                if (_loadingLayer.Visibility == Visibility.Visible) return;
                _loadingSince = Environment.TickCount64;
                _loadingLayer.Visibility = Visibility.Visible;
                if (_loadingLogo is null)
                {
                    _loadingLogo = new LogoWebView();
                    _loadingLayer.Children.Add(_loadingLogo.View);
                    _ = _loadingLogo.StartAsync("loading.html");
                }
                var token = ++_loadingShowToken;
                await Task.Delay(25_000);
                if (token == _loadingShowToken && _svc.SelectedId == id && _views.IsLoading(id!)) _views.StopLoading(id!);
                return;
            }
            if (_loadingLayer.Visibility == Visibility.Collapsed) return;
            var hide = ++_loadingHideToken;
            var wait = 700 - (Environment.TickCount64 - _loadingSince);
            if (wait > 0) await Task.Delay((int)wait);
            if (hide != _loadingHideToken) return;
            _loadingLayer.Visibility = Visibility.Collapsed;
            _loadingShowToken++;
        }
        catch (Exception)
        {
            _loadingLayer.Visibility = Visibility.Collapsed;
        }
    }

    // ---------------------------------------------------------------- Tutorial

    private TeachingTip? _tip;
    private int _tipStep;

    private sealed record TourStep(string Title, string Text, Func<FrameworkElement?> Target, Action? Enter = null);

    private IReadOnlyList<TourStep> TourSteps() =>
    [
        new TourStep("Bienvenido a AlternAIs",
            "Cada cuenta conserva su propia sesión. Cuando Claude te pida iniciar sesión, elegí tu correo electrónico y el código que te envía; «Continuar con Google» puede fallar en un navegador integrado.",
            () => null),
        new TourStep("Agregá tus cuentas",
            "Usá este botón para crear una sesión independiente. Poné el correo con el que iniciás sesión (nunca la contraseña).",
            () => _addButton),
        new TourStep("Cambiá y administrá sesiones",
            "Las cuentas aparecen en esta barra. Elegí una con un clic (o Ctrl+1…9, Ctrl+Tab); con clic derecho abrís sus opciones: recargar, marcar un límite, cerrar sesión o eliminarla.",
            () => _railScroll),
        new TourStep("Claude y Replit",
            "Arriba elegís la IA. Cada una tiene sus propias cuentas y su tema (Claude oscuro, Replit claro).",
            () => _railTop),
        new TourStep("Navegador, archivos y mensajes",
            "Abajo están el navegador interno (Ctrl+B), el explorador de archivos (Ctrl+E), los mensajes guardados (Ctrl+M) y los ajustes.",
            () => _railBottom),
        new TourStep("Ajustes",
            "Acá cambiás el tema (también tocando el logo), movés la barra de cuentas, elegís iniciar con Windows o seguir en la bandeja al cerrar, y exportás o importás cuentas. AlternAIs no es una aplicación oficial de Anthropic.",
            () => _panelHost,
            () => { if (_panel != SidePanel.Settings) TogglePanel(SidePanel.Settings); })
    ];

    private void StartTutorial()
    {
        _tipStep = 0;
        if (_tip is null)
        {
            _tip = new TeachingTip
            {
                IsLightDismissEnabled = false,
                CloseButtonContent = "Saltar tutorial",
                PreferredPlacement = TeachingTipPlacementMode.Auto
            };
            Grid.SetRowSpan(_tip, 2);
            _tip.ActionButtonClick += (_, _) =>
            {
                _tipStep++;
                if (_tipStep >= TourSteps().Count) FinishTutorial();
                else ShowTourStep();
            };
            _tip.CloseButtonClick += (_, _) => FinishTutorial();
            Root.Children.Add(_tip);
        }
        ShowTourStep();
    }

    private void ShowTourStep()
    {
        if (_tip is null) return;
        var steps = TourSteps();
        var step = steps[_tipStep];
        step.Enter?.Invoke();
        _tip.IsOpen = false;
        var target = step.Target();
        _tip.Target = target;
        _tip.PreferredPlacement = target is null ? TeachingTipPlacementMode.Center : TeachingTipPlacementMode.Auto;
        _tip.Title = step.Title;
        _tip.Subtitle = step.Text;
        _tip.ActionButtonContent = _tipStep == steps.Count - 1 ? "Terminar" : "Siguiente (" + (_tipStep + 1) + " de " + steps.Count + ")";
        _tip.RequestedTheme = CurrentPalette.ElementTheme;
        // Un respiro para que el cajón termine de aparecer antes de apuntarle.
        DispatcherQueue.TryEnqueue(Microsoft.UI.Dispatching.DispatcherQueuePriority.Low, () => { if (_tip is not null) _tip.IsOpen = true; });
    }

    private void FinishTutorial()
    {
        if (_tip is not null) _tip.IsOpen = false;
        if (!_svc.State.TutorialShown) _svc.MarkTutorialShown();
        if (_panel == SidePanel.Settings) TogglePanel(SidePanel.Settings);
    }
}
