using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.Web.WebView2.Core;

namespace AlternAIs.App.Web;

/// <summary>
/// Logo animado (arranque o bucle de carga). Para que sea idéntico al de Electron se dibuja con el mismo
/// splash.js en un WebView2 (Assets/ui, servido como https://alternais.ui/). Si WebView2 no arranca, queda el
/// fondo carbón y quien lo usa lo saca por tiempo.
/// </summary>
internal sealed class LogoWebView
{
    private const string Host = "alternais.ui";
    private static Task<CoreWebView2Environment>? _env;

    // Siempre visible: un WebView2 oculto puede no terminar de iniciarse. Mientras carga se ve el fondo carbón.
    public WebView2 View { get; } = new() { DefaultBackgroundColor = Ui.Logo.Charcoal };

    /// <summary>La animación de arranque terminó o se tocó para saltarla.</summary>
    public event Action? Finished;

    private static string AssetsDir => Path.Combine(AppContext.BaseDirectory, "Assets", "ui");

    /// <summary>Un único entorno para todos los logos (carpeta propia, separada de las cuentas).</summary>
    private static Task<CoreWebView2Environment> SharedEnvironment() => _env ??= CoreWebView2Environment
        .CreateWithOptionsAsync(null, Path.Combine(AccountWebViews.ProfilesDir, "ui"), new CoreWebView2EnvironmentOptions())
        .AsTask();

    /// <summary>
    /// <paramref name="page"/>: intro.html o loading.html. Espera a que el control esté en pantalla
    /// (iniciar un WebView2 antes de eso puede no terminar nunca).
    /// </summary>
    public async Task<bool> StartAsync(string page, bool light = false)
    {
        try
        {
            if (!View.IsLoaded)
            {
                var loaded = new TaskCompletionSource();
                View.Loaded += (_, _) => loaded.TrySetResult();
                await Task.WhenAny(loaded.Task, Task.Delay(3000));
            }
            if (!File.Exists(Path.Combine(AssetsDir, page))) return false;
            await View.EnsureCoreWebView2Async(await SharedEnvironment());
            var core = View.CoreWebView2;
            core.Settings.AreDefaultContextMenusEnabled = false;
            core.Settings.AreDevToolsEnabled = false;
            core.Settings.IsStatusBarEnabled = false;
            core.Settings.IsZoomControlEnabled = false;
            core.SetVirtualHostNameToFolderMapping(Host, AssetsDir, CoreWebView2HostResourceAccessKind.DenyCors);
            core.WebMessageReceived += (_, e) =>
            {
                string? msg = null;
                try { msg = e.TryGetWebMessageAsString(); } catch (Exception) { }
                if (msg == "done") Finished?.Invoke();
            };
            if (light) View.DefaultBackgroundColor = Microsoft.UI.Colors.White;
            core.Navigate("https://" + Host + "/" + page + (light ? "?light=1" : ""));
            return true;
        }
        catch (Exception)
        {
            return false;
        }
    }

    public void Close()
    {
        try { View.Close(); } catch (Exception) { }
    }
}
