using Microsoft.Windows.AppNotifications;
using Microsoft.Windows.AppNotifications.Builder;

namespace AlternAIs.App.Platform;

/// <summary>Avisos de Windows (toast). Si algo falla, la app sigue sin avisos.</summary>
internal static class Notifier
{
    private static bool _ready;

    public static void Register()
    {
        try
        {
            AppNotificationManager.Default.Register();
            _ready = true;
        }
        catch (Exception)
        {
            _ready = false;
        }
    }

    public static void Show(string title, string text)
    {
        if (!_ready) return;
        try
        {
            AppNotificationManager.Default.Show(new AppNotificationBuilder().AddText(title).AddText(text).BuildNotification());
        }
        catch (Exception)
        {
        }
    }
}
