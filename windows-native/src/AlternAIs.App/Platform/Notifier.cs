using AlternAIs.Domain.UseCases;
using Microsoft.Windows.AppNotifications;
using Microsoft.Windows.AppNotifications.Builder;

namespace AlternAIs.App.Platform;

/// <summary>Lo que se tocó en un aviso: acción («open», «reply», «snippet»), cuenta, mensaje guardado y lo escrito.</summary>
public sealed record NotificationAction(string Action, string AccountId, int Slot, string? Typed);

/// <summary>
/// Avisos de Windows (toast). El de fin de descanso trae una caja para responder y los dos primeros mensajes
/// guardados como botones (igual que Android). Si algo falla, la app sigue sin avisos.
/// </summary>
internal static class Notifier
{
    private const string ReplyBox = "reply";
    private static bool _ready;

    /// <summary>Se tocó un aviso con la app abierta (llega desde otro hilo).</summary>
    public static event Action<NotificationAction>? Invoked;

    public static void Register()
    {
        try
        {
            // El evento se engancha ANTES de Register (así lo pide el Windows App SDK).
            AppNotificationManager.Default.NotificationInvoked += (_, args) =>
            {
                var action = Parse(args.Arguments, args.UserInput);
                if (action is not null) Invoked?.Invoke(action);
            };
            AppNotificationManager.Default.Register();
            _ready = true;
        }
        catch (Exception)
        {
            _ready = false;
        }
    }

    /// <summary>Si la app se abrió tocando un aviso (estaba cerrada), qué se tocó.</summary>
    public static NotificationAction? FromLaunch()
    {
        try
        {
            var args = Microsoft.Windows.AppLifecycle.AppInstance.GetCurrent().GetActivatedEventArgs();
            if (args.Kind == Microsoft.Windows.AppLifecycle.ExtendedActivationKind.AppNotification &&
                args.Data is AppNotificationActivatedEventArgs n)
                return Parse(n.Arguments, n.UserInput);
        }
        catch (Exception)
        {
        }
        return null;
    }

    private static NotificationAction? Parse(IDictionary<string, string> arguments, IDictionary<string, string>? input)
    {
        if (!arguments.TryGetValue("action", out var action) || !arguments.TryGetValue("id", out var id)) return null;
        var slot = arguments.TryGetValue("slot", out var s) && int.TryParse(s, out var n) ? n : -1;
        string? typed = null;
        if (input is not null && input.TryGetValue(ReplyBox, out var t)) typed = t;
        return new NotificationAction(action, id, slot, typed);
    }

    /// <summary>Aviso simple. Con <paramref name="accountId"/>, tocarlo abre esa cuenta.</summary>
    public static void Show(string title, string text, string? accountId = null)
    {
        if (!_ready) return;
        try
        {
            var b = new AppNotificationBuilder().AddText(title).AddText(text);
            if (accountId is not null) b.AddArgument("action", "open").AddArgument("id", accountId);
            AppNotificationManager.Default.Show(b.BuildNotification());
        }
        catch (Exception)
        {
        }
    }

    /// <summary>Fin de descanso sin mensaje automático: caja para responder y botones con mensajes guardados.</summary>
    public static void ShowWithReply(string title, string text, string accountId, IReadOnlyList<SnippetAction> snippets)
    {
        if (!_ready) return;
        try
        {
            var b = new AppNotificationBuilder()
                .AddText(title)
                .AddText(text)
                .AddArgument("action", "open")
                .AddArgument("id", accountId)
                .AddTextBox(ReplyBox, "Escribí una respuesta…", "")
                .AddButton(new AppNotificationButton("Responder")
                    .AddArgument("action", "reply")
                    .AddArgument("id", accountId)
                    .SetInputId(ReplyBox));
            foreach (var s in snippets)
            {
                b.AddButton(new AppNotificationButton(s.Label)
                    .AddArgument("action", "snippet")
                    .AddArgument("id", accountId)
                    .AddArgument("slot", s.Slot.ToString(System.Globalization.CultureInfo.InvariantCulture)));
            }
            AppNotificationManager.Default.Show(b.BuildNotification());
        }
        catch (Exception)
        {
            Show(title, text, accountId);
        }
    }
}
