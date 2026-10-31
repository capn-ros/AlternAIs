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
        Platform.Notifier.Register();
        _window = new MainWindow();
        _window.Activate();
    }
}
