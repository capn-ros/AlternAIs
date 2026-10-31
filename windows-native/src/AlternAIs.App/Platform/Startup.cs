using Microsoft.Win32;

namespace AlternAIs.App.Platform;

/// <summary>Iniciar con Windows: una entrada en HKCU\...\Run (sin permisos de administrador).</summary>
internal static class Startup
{
    private const string Key = @"Software\Microsoft\Windows\CurrentVersion\Run";
    private const string Name = "AlternAIs";

    public static void Set(bool enabled)
    {
        try
        {
            using var key = Registry.CurrentUser.OpenSubKey(Key, writable: true);
            if (key is null) return;
            if (enabled) key.SetValue(Name, "\"" + Environment.ProcessPath + "\"");
            else key.DeleteValue(Name, throwOnMissingValue: false);
        }
        catch (Exception)
        {
        }
    }
}
