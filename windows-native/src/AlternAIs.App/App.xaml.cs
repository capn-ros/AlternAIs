using Microsoft.UI.Xaml;

namespace AlternAIs.App;

public partial class App : Microsoft.UI.Xaml.Application
{
    private MainWindow? _window;

    public App()
    {
        InitializeComponent();
    }

    protected override void OnLaunched(LaunchActivatedEventArgs args)
    {
        // Ya hay una AlternAIs abierta: se la trae al frente y esta se cierra.
        if (!Platform.SingleInstance.Claim())
        {
            Exit();
            return;
        }
        var fromNotification = Platform.Notifier.FromLaunch();
        Platform.Notifier.Register();
        _window = new MainWindow(fromNotification);
        _window.Activate();
    }
}
