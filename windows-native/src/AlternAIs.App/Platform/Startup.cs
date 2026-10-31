using Microsoft.Win32;

namespace AlternAIs.App.Platform;

/// <summary>Iniciar con Windows: una entrada en HKCU\...\Run (sin permisos de administrador).</summary>
internal static class Startup
{
    private const string Key = @"Software\Microsoft\Windows\CurrentVersion\Run";
    private const string Name = "AlternAIs";

    /// <summary>Argumento con el que arranca Windows: si «Cerrar a la bandeja» está activo, la app empieza escondida.</summary>
    public const string HiddenArg = "--hidden";

    public static bool LaunchedHidden => Environment.GetCommandLineArgs().Any(a => a == HiddenArg);

    public static void Set(bool enabled)
    {
        try
        {
            using var key = Registry.CurrentUser.OpenSubKey(Key, writable: true);
            if (key is null) return;
            if (enabled) key.SetValue(Name, "\"" + Environment.ProcessPath + "\" " + HiddenArg);
            else key.DeleteValue(Name, throwOnMissingValue: false);
        }
        catch (Exception)
        {
        }
    }
}
