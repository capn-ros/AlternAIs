using AlternAIs.Domain.Models;
using AlternAIs.Domain.Policies;
using AlternAIs.Domain.UseCases;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.Web.WebView2.Core;
using Windows.System;

namespace AlternAIs.App.Web;

/// <summary>
/// Un WebView2 por cuenta, cada uno con su propia carpeta de datos (cookies y sesión aisladas, igual que
/// los perfiles de Android). Se crean al usarse por primera vez y se mantienen vivos para no perder la página.
/// </summary>
internal sealed class AccountWebViews
{
    private readonly Grid _host;
    private readonly Dictionary<string, WebView2> _views = new();
    private readonly HashSet<string> _loading = new();
    private string? _shown;

    public AccountWebViews(Grid host) => _host = host;

    /// <summary>Se llama cada vez que una cuenta cambia de página (para recordar la última).</summary>
    public event Action<string, string>? UrlChanged;

    /// <summary>Una página pidió un atajo de la app (Ctrl+Tab, Ctrl+1…): la combinación, p. ej. «ctrl+tab».</summary>
    public event Action<string>? ShortcutPressed;

    /// <summary>Cambió qué cuentas están cargando (para mostrar el logo en bucle).</summary>
    public event Action? LoadingChanged;

    public static string ProfilesDir =>
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Claudeck", "WebView2");

    private bool _dark = true;

    /// <summary>Tema de las páginas (prefers-color-scheme): oscuro o claro, igual que el de la app.</summary>
    public void SetDark(bool dark)
    {
        _dark = dark;
        foreach (var wv in _views.Values) ApplyScheme(wv.CoreWebView2);
    }

    private void ApplyScheme(CoreWebView2? core)
    {
        if (core is null) return;
        try { core.Profile.PreferredColorScheme = _dark ? CoreWebView2PreferredColorScheme.Dark : CoreWebView2PreferredColorScheme.Light; }
        catch (Exception) { }
    }

    public bool Has(string id) => _views.ContainsKey(id);

    /// <summary>¿La página de esta cuenta está cargando por primera vez (o por «Recargar»)?</summary>
    public bool IsLoading(string id) => _loading.Contains(id);

    public async Task<WebView2> GetAsync(Account account, AiService service)
    {
        if (_views.TryGetValue(account.Id, out var existing))
        {
            await existing.EnsureCoreWebView2Async(); // espera si todavía se está iniciando
            return existing;
        }
        // Nace VISIBLE (abajo de todo, tapada por la cuenta activa y el logo de carga): un WebView2 oculto puede no
        // terminar de iniciarse nunca. Después ShowOnly deja visible solo la cuenta activa.
        var wv = new WebView2 { DefaultBackgroundColor = Ui.Logo.Charcoal };
        _views[account.Id] = wv;
        _host.Children.Insert(0, wv); // debajo del logo de carga y del aviso de cuenta vacía
        SetLoading(account.Id, true);
        try
        {
            var env = await CoreWebView2Environment.CreateWithOptionsAsync(null, Path.Combine(ProfilesDir, account.ProfileName), new CoreWebView2EnvironmentOptions());
            await wv.EnsureCoreWebView2Async(env);
            await Configure(wv, account.Id, service);
        }
        catch (Exception)
        {
            SetLoading(account.Id, false);
            throw;
        }
        var start = UrlRestorePolicy.Restorable(service, account.LastUrl) ?? service.StartUrl();
        wv.CoreWebView2.Navigate(start);
        // Ya iniciada, se puede ocultar si no es la que se está mostrando (p. ej. un envío automático de fondo).
        wv.Visibility = account.Id == _shown ? Visibility.Visible : Visibility.Collapsed;
        return wv;
    }

    /// <summary>Para el envío en segundo plano: la vista si ya existe (sin crearla).</summary>
    public WebView2? Peek(string id) => _views.TryGetValue(id, out var wv) ? wv : null;

    private async Task Configure(WebView2 wv, string id, AiService service)
    {
        var core = wv.CoreWebView2;
        ApplyScheme(core);
        core.NewWindowRequested += (_, e) =>
        {
            e.Handled = true;
            if (!Uri.TryCreate(e.Uri, UriKind.Absolute, out var uri)) return;
            if (uri.Scheme != Uri.UriSchemeHttps && uri.Scheme != Uri.UriSchemeHttp) return;
            if (service.OwnsHost(uri.Host.ToLowerInvariant())) core.Navigate(e.Uri);
            else { var launch = Launcher.LaunchUriAsync(uri); }
        };
        core.SourceChanged += (_, _) => UrlChanged?.Invoke(id, core.Source);
        core.NavigationCompleted += (_, _) => SetLoading(id, false);
        core.PermissionRequested += (_, e) =>
        {
            var kind = e.PermissionKind switch
            {
                CoreWebView2PermissionKind.Microphone => WebPermission.Microphone,
                CoreWebView2PermissionKind.Camera => WebPermission.Camera,
                CoreWebView2PermissionKind.Notifications => WebPermission.Notifications,
                CoreWebView2PermissionKind.ClipboardRead => WebPermission.ClipboardRead,
                _ => WebPermission.Other
            };
            switch (WebPermissionPolicy.Decide(kind, e.Uri, service))
            {
                case PermissionDecision.Allow: e.State = CoreWebView2PermissionState.Allow; break;
                case PermissionDecision.Deny: e.State = CoreWebView2PermissionState.Deny; break;
            }
        };
        await AttachShortcuts(core, combo => ShortcutPressed?.Invoke(combo));
    }

    /// <summary>
    /// Las teclas que llegan a una página no pasan por la ventana: un script chico avisa de los atajos de la app
    /// (y solo de esos; el resto sigue siendo de la página). También lo usa el navegador interno.
    /// </summary>
    public static async Task AttachShortcuts(CoreWebView2 core, Action<string> onCombo)
    {
        core.WebMessageReceived += (_, e) =>
        {
            string? msg = null;
            try { msg = e.TryGetWebMessageAsString(); } catch (Exception) { }
            var combo = Shortcuts.ComboFromMessage(msg);
            if (combo is not null) onCombo(combo);
        };
        try { await core.AddScriptToExecuteOnDocumentCreatedAsync(Shortcuts.PageScript()); }
        catch (Exception) { }
    }

    private void SetLoading(string id, bool on)
    {
        var changed = on ? _loading.Add(id) : _loading.Remove(id);
        if (changed) LoadingChanged?.Invoke();
    }

    /// <summary>Muestra solo la vista de [id] (o ninguna).</summary>
    public void ShowOnly(string? id)
    {
        _shown = id;
        foreach (var (key, wv) in _views)
            wv.Visibility = key == id ? Visibility.Visible : Visibility.Collapsed;
    }

    /// <summary>Recarga mostrando el logo en bucle hasta que termine.</summary>
    public void Reload(string id)
    {
        if (!_views.TryGetValue(id, out var wv) || wv.CoreWebView2 is null) return;
        SetLoading(id, true);
        wv.CoreWebView2.Reload();
    }

    /// <summary>Por si una página nunca avisa que terminó: deja de mostrarse como «cargando».</summary>
    public void StopLoading(string id) => SetLoading(id, false);

    public void Focus(string id)
    {
        if (_views.TryGetValue(id, out var wv)) wv.Focus(FocusState.Programmatic);
    }

    /// <summary>Cierra la sesión: borra cookies y datos del sitio de esa cuenta y vuelve a la página de inicio.</summary>
    public async Task SignOutAsync(Account account, AiService service)
    {
        var wv = await GetAsync(account, service);
        await wv.CoreWebView2.Profile.ClearBrowsingDataAsync();
        SetLoading(account.Id, true);
        wv.CoreWebView2.Navigate(service.StartUrl());
    }

    /// <summary>Elimina la vista y la carpeta de datos de la cuenta (su sesión).</summary>
    public async Task DeleteAsync(Account account)
    {
        SetLoading(account.Id, false);
        if (_views.Remove(account.Id, out var wv))
        {
            _host.Children.Remove(wv);
            try { wv.Close(); } catch (Exception) { }
            await Task.Delay(800); // da tiempo a que el proceso suelte la carpeta
        }
        try
        {
            var dir = Path.Combine(ProfilesDir, account.ProfileName);
            if (Directory.Exists(dir)) Directory.Delete(dir, recursive: true);
        }
        catch (Exception)
        {
            // Si quedó en uso, el resto se borra la próxima vez que se abra la app (ver PurgeOrphans).
        }
    }

    /// <summary>Borra las carpetas de sesión de cuentas que ya no existen.</summary>
    public static void PurgeOrphans(IEnumerable<string> liveProfileNames)
    {
        try
        {
            if (!Directory.Exists(ProfilesDir)) return;
            var live = new HashSet<string>(liveProfileNames);
            foreach (var dir in Directory.GetDirectories(ProfilesDir))
            {
                var name = Path.GetFileName(dir);
                if (name.StartsWith("acc_", StringComparison.Ordinal) && !live.Contains(name))
                {
                    try { Directory.Delete(dir, recursive: true); } catch (Exception) { }
                }
            }
        }
        catch (Exception)
        {
        }
    }
}
