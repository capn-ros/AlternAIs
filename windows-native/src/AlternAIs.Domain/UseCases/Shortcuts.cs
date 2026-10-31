namespace AlternAIs.Domain.UseCases;

/// <summary>Qué hace cada atajo de teclado de la ventana.</summary>
public enum ShortcutAction
{
    NextAccount,
    PreviousAccount,
    SelectAccount,
    AddAccount,
    ToggleBrowser,
    ToggleSnippets,
    ToggleFiles,
    ToggleSettings,
    ClosePanel
}

/// <param name="Index">Solo para <see cref="ShortcutAction.SelectAccount"/>: posición (0 = Ctrl+1).</param>
public sealed record Shortcut(ShortcutAction Action, int Index = 0);

/// <summary>
/// Atajos de la app. Se usan dos veces: en la interfaz (cuando el foco está en la barra o un cajón) y dentro de
/// cada página web, que de otro modo se queda con las teclas. La página manda la combinación como texto
/// («ctrl+shift+tab») y <see cref="FromCombo"/> la traduce.
/// </summary>
public static class Shortcuts
{
    /// <summary>Combinaciones que la página tiene que dejar pasar a la app (las demás siguen siendo de la página).</summary>
    public static readonly IReadOnlyList<string> PageCombos =
    [
        "ctrl+tab", "ctrl+shift+tab",
        "ctrl+1", "ctrl+2", "ctrl+3", "ctrl+4", "ctrl+5", "ctrl+6", "ctrl+7", "ctrl+8", "ctrl+9",
        "ctrl+n", "ctrl+b", "ctrl+m", "ctrl+e", "ctrl+,"
    ];

    /// <summary>
    /// Arma la combinación en el mismo formato que el script de la página: modificadores en orden fijo y la tecla
    /// en minúscula («Tab» → «tab», «N» → «n»). Null si no hay Ctrl ni Alt (las teclas sueltas son de la página).
    /// </summary>
    public static string? Combo(bool ctrl, bool shift, bool alt, string key)
    {
        if (!ctrl && !alt) return null;
        if (string.IsNullOrEmpty(key)) return null;
        var k = key.ToLowerInvariant();
        if (k is "control" or "shift" or "alt") return null;
        return (ctrl ? "ctrl+" : "") + (alt ? "alt+" : "") + (shift ? "shift+" : "") + k;
    }

    /// <summary>Qué hacer con una combinación (null = no es un atajo de la app).</summary>
    public static Shortcut? FromCombo(string? combo) => combo switch
    {
        "ctrl+tab" => new Shortcut(ShortcutAction.NextAccount),
        "ctrl+shift+tab" => new Shortcut(ShortcutAction.PreviousAccount),
        "ctrl+n" => new Shortcut(ShortcutAction.AddAccount),
        "ctrl+b" => new Shortcut(ShortcutAction.ToggleBrowser),
        "ctrl+m" => new Shortcut(ShortcutAction.ToggleSnippets),
        "ctrl+e" => new Shortcut(ShortcutAction.ToggleFiles),
        "ctrl+," => new Shortcut(ShortcutAction.ToggleSettings),
        "escape" => new Shortcut(ShortcutAction.ClosePanel),
        { Length: 6 } c when c.StartsWith("ctrl+", StringComparison.Ordinal) && c[5] is >= '1' and <= '9'
            => new Shortcut(ShortcutAction.SelectAccount, c[5] - '1'),
        _ => null
    };

    /// <summary>Script que se inyecta en cada página: avisa a la app de sus atajos y evita que la página los use.</summary>
    public static string PageScript()
    {
        var list = string.Join(",", PageCombos.Select(c => "'" + c + "'"));
        return "(function(){if(window.__alternKeys)return;window.__alternKeys=1;var L=[" + list + "];" +
               "window.addEventListener('keydown',function(e){" +
               "if(!(e.ctrlKey||e.altKey)||!e.key)return;" +
               "var k=e.key.toLowerCase();if(k==='control'||k==='shift'||k==='alt')return;" +
               "var c=(e.ctrlKey?'ctrl+':'')+(e.altKey?'alt+':'')+(e.shiftKey?'shift+':'')+k;" +
               "if(L.indexOf(c)<0)return;e.preventDefault();e.stopPropagation();" +
               "try{window.chrome.webview.postMessage('key:'+c);}catch(_){}" +
               "},true);})();";
    }

    /// <summary>El mensaje que manda el script («key:ctrl+tab») → la combinación, o null si es otra cosa.</summary>
    public static string? ComboFromMessage(string? message) =>
        message is not null && message.StartsWith("key:", StringComparison.Ordinal) && message.Length <= 40
            ? message[4..]
            : null;
}
