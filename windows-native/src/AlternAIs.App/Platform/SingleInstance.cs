using System.Runtime.InteropServices;

namespace AlternAIs.App.Platform;

/// <summary>
/// Una sola AlternAIs abierta a la vez (como la versión Electron): dos copias se pisarían el accounts.json
/// y las sesiones. Si ya hay una, se la trae al frente (aunque esté escondida en la bandeja) y esta se cierra.
/// </summary>
internal static class SingleInstance
{
    private const string MutexName = "AlternAIs.SingleInstance.v1";
    public const string WindowTitle = "AlternAIs";
    private static Mutex? _mutex;

    /// <summary>True si esta es la primera; false si ya había otra (y ya se la mostró).</summary>
    public static bool Claim()
    {
        try
        {
            _mutex = new Mutex(initiallyOwned: true, MutexName, out var created);
            if (created) return true;
            BringOtherToFront();
            return false;
        }
        catch (Exception)
        {
            return true; // ante la duda, abrir
        }
    }

    private static void BringOtherToFront()
    {
        try
        {
            var hwnd = FindWindowW("WinUIDesktopWin32WindowClass", WindowTitle);
            if (hwnd == IntPtr.Zero) return;
            ShowWindow(hwnd, IsIconic(hwnd) ? 9 /* SW_RESTORE */ : 5 /* SW_SHOW */);
            SetForegroundWindow(hwnd);
        }
        catch (Exception)
        {
        }
    }

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern IntPtr FindWindowW(string? className, string? title);

    [DllImport("user32.dll")]
    private static extern bool ShowWindow(IntPtr hWnd, int cmd);

    [DllImport("user32.dll")]
    private static extern bool IsIconic(IntPtr hWnd);

    [DllImport("user32.dll")]
    private static extern bool SetForegroundWindow(IntPtr hWnd);
}
