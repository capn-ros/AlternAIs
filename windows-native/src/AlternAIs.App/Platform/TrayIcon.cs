using System.Runtime.InteropServices;
using AlternAIs.Application;

namespace AlternAIs.App.Platform;

/// <summary>
/// Ícono en la bandeja del sistema (WinUI 3 no trae uno). Usa Shell_NotifyIcon y recibe los clics
/// subclasificando la ventana principal. Clic izquierdo = abrir; clic derecho = menú con las cuentas
/// (<see cref="TrayMenu"/>). Si algo falla, la app sigue sin ícono (y entonces cerrar la cierra).
/// </summary>
internal sealed class TrayIcon : IDisposable
{
    private const uint WmApp = 0x8000;
    private const uint CallbackMessage = WmApp + 17;
    private const uint WmLButtonUp = 0x0202;
    private const uint WmRButtonUp = 0x0205;
    private const uint WmNull = 0x0000;
    private const uint NimAdd = 0, NimModify = 1, NimDelete = 2;
    private const uint NifMessage = 0x1, NifIcon = 0x2, NifTip = 0x4;
    private const uint MfString = 0x0, MfGrayed = 0x1, MfSeparator = 0x800;
    private const uint TpmRightButton = 0x0002, TpmReturnCmd = 0x0100, TpmNoNotify = 0x0080;
    private const int CmdBase = 1000;

    private readonly IntPtr _hwnd;
    private readonly SubclassProc _proc; // referencia viva: si el GC la junta, Windows llamaría a un puntero muerto
    private readonly uint _taskbarCreated;
    private readonly IntPtr _icon;
    private bool _added;
    private TrayMenuModel _model = new(TrayMenu.AppName, []);

    /// <summary>Clic izquierdo o «Abrir AlternAIs».</summary>
    public event Action? OpenRequested;

    /// <summary>Se eligió una cuenta del menú.</summary>
    public event Action<TrayEntry>? AccountRequested;

    /// <summary>«Salir».</summary>
    public event Action? QuitRequested;

    public TrayIcon(IntPtr hwnd, string iconPath)
    {
        _hwnd = hwnd;
        _proc = WndProc;
        _taskbarCreated = RegisterWindowMessageW("TaskbarCreated");
        var size = GetSystemMetrics(49); // SM_CXSMICON
        _icon = LoadImageW(IntPtr.Zero, iconPath, 1 /* IMAGE_ICON */, size, size, 0x10 /* LR_LOADFROMFILE */);
        SetWindowSubclass(_hwnd, _proc, (UIntPtr)0xA17E, IntPtr.Zero);
    }

    public bool Visible => _added;

    /// <summary>Muestra el ícono (o lo actualiza) con el menú y el globo de <paramref name="model"/>.</summary>
    public void Show(TrayMenuModel model)
    {
        _model = model;
        var data = Data(model.Tooltip);
        _added = Shell_NotifyIconW(_added ? NimModify : NimAdd, ref data) || _added;
    }

    public void Hide()
    {
        if (!_added) return;
        var data = Data("");
        Shell_NotifyIconW(NimDelete, ref data);
        _added = false;
    }

    private NotifyIconData Data(string tip) => new()
    {
        cbSize = (uint)Marshal.SizeOf<NotifyIconData>(),
        hWnd = _hwnd,
        uID = 1,
        uFlags = NifMessage | NifIcon | NifTip,
        uCallbackMessage = CallbackMessage,
        hIcon = _icon,
        szTip = tip.Length > 127 ? tip[..127] : tip,
        szInfo = "",
        szInfoTitle = ""
    };

    private IntPtr WndProc(IntPtr hWnd, uint msg, IntPtr wParam, IntPtr lParam, UIntPtr id, IntPtr data)
    {
        if (msg == CallbackMessage)
        {
            var mouse = (uint)(lParam.ToInt64() & 0xFFFF);
            if (mouse == WmLButtonUp) OpenRequested?.Invoke();
            else if (mouse == WmRButtonUp) ShowMenu();
            return IntPtr.Zero;
        }
        if (msg == _taskbarCreated && _added)
        {
            // El Explorador se reinició: hay que volver a poner el ícono.
            _added = false;
            Show(_model);
        }
        return DefSubclassProc(hWnd, msg, wParam, lParam);
    }

    private void ShowMenu()
    {
        var menu = CreatePopupMenu();
        if (menu == IntPtr.Zero) return;
        try
        {
            var entries = _model.Entries;
            for (var i = 0; i < entries.Count; i++)
            {
                var e = entries[i];
                switch (e.Kind)
                {
                    case TrayEntryKind.Separator:
                        AppendMenuW(menu, MfSeparator, UIntPtr.Zero, null);
                        break;
                    case TrayEntryKind.Header:
                        AppendMenuW(menu, MfString | MfGrayed, UIntPtr.Zero, e.Text);
                        break;
                    default:
                        AppendMenuW(menu, MfString, (UIntPtr)(uint)(CmdBase + i), e.Text);
                        break;
                }
            }
            GetCursorPos(out var pt);
            SetForegroundWindow(_hwnd); // si no, el menú no se cierra al tocar afuera
            var cmd = TrackPopupMenuEx(menu, TpmRightButton | TpmReturnCmd | TpmNoNotify, pt.X, pt.Y, _hwnd, IntPtr.Zero);
            PostMessageW(_hwnd, WmNull, IntPtr.Zero, IntPtr.Zero);
            var index = cmd - CmdBase;
            if (cmd >= CmdBase && index < entries.Count)
            {
                var chosen = entries[index];
                switch (chosen.Kind)
                {
                    case TrayEntryKind.Open: OpenRequested?.Invoke(); break;
                    case TrayEntryKind.Quit: QuitRequested?.Invoke(); break;
                    case TrayEntryKind.Account: AccountRequested?.Invoke(chosen); break;
                }
            }
        }
        finally
        {
            DestroyMenu(menu);
        }
    }

    public void Dispose()
    {
        Hide();
        try { RemoveWindowSubclass(_hwnd, _proc, (UIntPtr)0xA17E); } catch (Exception) { }
        if (_icon != IntPtr.Zero) DestroyIcon(_icon);
    }

    // ---------------------------------------------------------------- Win32

    private delegate IntPtr SubclassProc(IntPtr hWnd, uint msg, IntPtr wParam, IntPtr lParam, UIntPtr id, IntPtr refData);

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    private struct NotifyIconData
    {
        public uint cbSize;
        public IntPtr hWnd;
        public uint uID;
        public uint uFlags;
        public uint uCallbackMessage;
        public IntPtr hIcon;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 128)] public string szTip;
        public uint dwState;
        public uint dwStateMask;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 256)] public string szInfo;
        public uint uVersion;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 64)] public string szInfoTitle;
        public uint dwInfoFlags;
        public Guid guidItem;
        public IntPtr hBalloonIcon;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct Point
    {
        public int X;
        public int Y;
    }

    [DllImport("shell32.dll", CharSet = CharSet.Unicode)]
    private static extern bool Shell_NotifyIconW(uint message, ref NotifyIconData data);

    [DllImport("comctl32.dll")]
    private static extern bool SetWindowSubclass(IntPtr hWnd, SubclassProc proc, UIntPtr id, IntPtr refData);

    [DllImport("comctl32.dll")]
    private static extern bool RemoveWindowSubclass(IntPtr hWnd, SubclassProc proc, UIntPtr id);

    [DllImport("comctl32.dll")]
    private static extern IntPtr DefSubclassProc(IntPtr hWnd, uint msg, IntPtr wParam, IntPtr lParam);

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern uint RegisterWindowMessageW(string name);

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern IntPtr LoadImageW(IntPtr hInst, string name, uint type, int cx, int cy, uint load);

    [DllImport("user32.dll")]
    private static extern bool DestroyIcon(IntPtr icon);

    [DllImport("user32.dll")]
    private static extern int GetSystemMetrics(int index);

    [DllImport("user32.dll")]
    private static extern IntPtr CreatePopupMenu();

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern bool AppendMenuW(IntPtr menu, uint flags, UIntPtr id, string? text);

    [DllImport("user32.dll")]
    private static extern int TrackPopupMenuEx(IntPtr menu, uint flags, int x, int y, IntPtr hWnd, IntPtr tpm);

    [DllImport("user32.dll")]
    private static extern bool DestroyMenu(IntPtr menu);

    [DllImport("user32.dll")]
    private static extern bool GetCursorPos(out Point point);

    [DllImport("user32.dll")]
    private static extern bool SetForegroundWindow(IntPtr hWnd);

    [DllImport("user32.dll")]
    private static extern bool PostMessageW(IntPtr hWnd, uint msg, IntPtr wParam, IntPtr lParam);
}
