using AlternAIs.Domain.Models;

namespace AlternAIs.Domain.Policies;

/// <summary>Permisos que una página puede pedir (los que la app decide; el resto los pregunta el navegador).</summary>
public enum WebPermission { Microphone, Camera, Notifications, ClipboardRead, Other }

public enum PermissionDecision { Allow, Deny, Ask }

/// <summary>
/// Igual que Android: el micrófono (dictado por voz) solo se concede a las páginas https del propio servicio;
/// la cámara se niega siempre; las notificaciones del sitio se permiten solo al servicio; lo demás se pregunta.
/// </summary>
public static class WebPermissionPolicy
{
    public static PermissionDecision Decide(WebPermission kind, string? pageUrl, AiService service)
    {
        var own = IsOwnHttps(pageUrl, service);
        return kind switch
        {
            WebPermission.Microphone => own ? PermissionDecision.Allow : PermissionDecision.Deny,
            WebPermission.Camera => PermissionDecision.Deny,
            WebPermission.Notifications => own ? PermissionDecision.Allow : PermissionDecision.Deny,
            _ => PermissionDecision.Ask
        };
    }

    private static bool IsOwnHttps(string? url, AiService service) =>
        Uri.TryCreate(url, UriKind.Absolute, out var u) && u.Scheme == Uri.UriSchemeHttps &&
        service.OwnsHost(u.Host.ToLowerInvariant());
}
