using System.Globalization;
using AlternAIs.Domain.Models;

namespace AlternAIs.Domain.UseCases;

/// <summary>Textos de estado de una cuenta (mismos que Android: barra de tiempo, círculos y accesibilidad).</summary>
public static class AccountStatusText
{
    private const string Separator = " · ";

    /// <summary>Descripción del círculo: «Trabajo» o «Trabajo, en descanso, faltan 2 h 10 min».</summary>
    public static string CircleDescription(Account a, long now) =>
        a.IsResting(now) ? a.Name + ", en descanso, faltan " + RemainingTime.Format(a.LimitUntil - now) : a.Name;

    /// <summary>Barra de tiempo: «Trabajo · faltan 2 h 10 min» o «Trabajo · Disponible».</summary>
    public static string TimeBarText(Account a, long now) =>
        a.IsResting(now) ? a.Name + Separator + "faltan " + RemainingTime.Format(a.LimitUntil - now) : a.Name + Separator + "Disponible";

    /// <summary>Línea completa: nombre · propósito (si hay) · estado («En descanso hasta jue 21:00 (faltan 3 h)»).</summary>
    public static string InfoLine(Account a, long now, TimeZoneInfo zone, CultureInfo culture)
    {
        var parts = new List<string> { a.Name };
        if (!string.IsNullOrWhiteSpace(a.Purpose)) parts.Add(a.Purpose);
        if (a.IsResting(now))
        {
            var local = TimeZoneInfo.ConvertTime(DateTimeOffset.FromUnixTimeMilliseconds(a.LimitUntil), zone);
            var day = culture.DateTimeFormat.GetAbbreviatedDayName(local.DayOfWeek).TrimEnd('.');
            parts.Add($"En descanso hasta {day} {local.Hour:00}:{local.Minute:00} (faltan {RemainingTime.Format(a.LimitUntil - now)})");
        }
        else
        {
            parts.Add("Disponible");
        }
        return string.Join(Separator, parts);
    }

    /// <summary>Texto del globo de ayuda del círculo: estado en la primera línea y propósito · correo en la segunda.</summary>
    public static string Tooltip(Account a, long now)
    {
        var extra = string.Join(Separator, new[] { a.Purpose, a.Email }.Where(x => !string.IsNullOrWhiteSpace(x)));
        return TimeBarText(a, now) + (extra.Length > 0 ? "\n" + extra : "");
    }
}

/// <summary>Un mensaje guardado ofrecido como botón del aviso. <paramref name="Slot"/> es su posición (0 o 1).</summary>
public sealed record SnippetAction(int Slot, string Label, string Text);

/// <summary>Textos de los avisos de fin de descanso y del resultado del envío (los mismos que en Android).</summary>
public static class LimitNotificationText
{
    public static string DoneTitle(string name) => "«" + name + "» ya puede usarse";

    public static string DoneText(bool auto) => auto
        ? "Terminó el descanso. Enviando el mensaje automático…"
        : "Terminó el descanso por límite de uso. Podés responder desde acá.";

    /// <summary>Con mensaje automático no se ofrece responder a mano.</summary>
    public static bool OffersReply(bool auto) => !auto;

    /// <summary>Los dos primeros mensajes guardados (los vacíos se saltan pero conservan su lugar).</summary>
    public static IReadOnlyList<SnippetAction> SnippetActions(IReadOnlyList<string> snippets) =>
        snippets.Take(2)
            .Select((s, k) => (s, k))
            .Where(x => !string.IsNullOrWhiteSpace(x.s))
            .Select(x => new SnippetAction(x.k, x.s.Length > 22 ? x.s[..22] + "…" : x.s, x.s))
            .ToList();

    public static string ResultTitle(string name, bool ok) =>
        ok ? "Mensaje enviado a «" + name + "»" : "No se pudo enviar el mensaje a «" + name + "»";

    public static string ResultText(string text, bool ok)
    {
        var shortText = text.Length > 80 ? text[..80] + "…" : text;
        return ok ? "«" + shortText + "»" : "Copié el mensaje: abrí la cuenta y pegalo. «" + shortText + "»";
    }

    /// <summary>Lo escrito en el aviso o, si no se escribió nada, el mensaje guardado elegido. Null si queda vacío.</summary>
    public static string? ReplyText(string? typed, string? preset)
    {
        var t = (string.IsNullOrEmpty(typed) ? preset : typed)?.Trim();
        return string.IsNullOrEmpty(t) ? null : t;
    }
}
