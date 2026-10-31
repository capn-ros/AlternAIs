using AlternAIs.Domain.Models;
using AlternAIs.Domain.Policies;
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

    public AccountWebViews(Grid host) => _host = host;

    /// <summary>Se llama cada vez que una cuenta cambia de página (para recordar la última).</summary>
    public event Action<string, string>? UrlChanged;

    public static string ProfilesDir =>
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Claudeck", "WebView2");

    public bool Has(string id) => _views.ContainsKey(id);

    public async Task<WebView2> GetAsync(Account account, AiService service)
    {
        if (_views.TryGetValue(account.Id, out var existing))
        {
            await existing.EnsureCoreWebView2Async(); // espera si todavía se está iniciando
            return existing;
        }
        var wv = new WebView2 { Visibility = Visibility.Collapsed };
        _views[account.Id] = wv;
        _host.Children.Add(wv);
        var env = await CoreWebView2Environment.CreateAsync(null, Path.Combine(ProfilesDir, account.ProfileName));
        await wv.EnsureCoreWebView2Async(env);
        Configure(wv, account.Id, service);
        var start = UrlRestorePolicy.Restorable(service, account.LastUrl) ?? service.StartUrl();
        wv.CoreWebView2.Navigate(start);
        return wv;
    }

    private void Configure(WebView2 wv, string id, AiService service)
    {
        var core = wv.CoreWebView2;
        core.NewWindowRequested += (_, e) =>
        {
            e.Handled = true;
            if (!Uri.TryCreate(e.Uri, UriKind.Absolute, out var uri)) return;
            if (uri.Scheme != Uri.UriSchemeHttps && uri.Scheme != Uri.UriSchemeHttp) return;
            if (service.OwnsHost(uri.Host.ToLowerInvariant())) core.Navigate(e.Uri);
            else _ = Launcher.LaunchUriAsync(uri);
        };
        core.SourceChanged += (_, _) => UrlChanged?.Invoke(id, core.Source);
    }

    /// <summary>Muestra solo la vista de [id] (o ninguna).</summary>
    public void ShowOnly(string? id)
    {
        foreach (var (key, wv) in _views)
            wv.Visibility = key == id ? Visibility.Visible : Visibility.Collapsed;
    }

    public void Reload(string id)
    {
        if (_views.TryGetValue(id, out var wv)) wv.CoreWebView2?.Reload();
    }

    /// <summary>Cierra la sesión: borra cookies y datos del sitio de esa cuenta y vuelve a la página de inicio.</summary>
    public async Task SignOutAsync(Account account, AiService service)
    {
        var wv = await GetAsync(account, service);
        await wv.CoreWebView2.Profile.ClearBrowsingDataAsync();
        wv.CoreWebView2.Navigate(service.StartUrl());
    }

    /// <summary>Elimina la vista y la carpeta de datos de la cuenta (su sesión).</summary>
    public async Task DeleteAsync(Account account)
    {
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
