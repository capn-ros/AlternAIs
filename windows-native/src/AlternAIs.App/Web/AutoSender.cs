using AlternAIs.Application;
using AlternAIs.Domain.Models;
using AlternAIs.Domain.UseCases;

namespace AlternAIs.App.Web;

/// <summary>Escribe (y envía) un texto en el chat de una cuenta, con los mismos tiempos y reintentos que Android.</summary>
internal sealed class AutoSender(AccountWebViews views)
{
    /// <summary>Devuelve true si el script encontró la caja de texto y el texto salió.</summary>
    public async Task<bool> SendAsync(Account account, AiService service, string text, bool send = true)
    {
        using var cts = new CancellationTokenSource(AutoSendPolicy.TotalTimeoutMs);
        try
        {
            // Crear la vista también cuenta para el tope de tiempo (con la ventana en la bandeja puede demorar).
            var getting = views.GetAsync(account, service);
            if (await Task.WhenAny(getting, Task.Delay(AutoSendPolicy.TotalTimeoutMs / 2, cts.Token)) != getting) return false;
            var wv = await getting;
            await Task.Delay(AutoSendPolicy.FirstAttemptDelayMs, cts.Token);
            var script = AutoSendScript.Build(text, send);
            for (var attempt = 0; ; attempt++)
            {
                var result = await wv.CoreWebView2.ExecuteScriptAsync(script);
                switch (AutoSendPolicy.Next(attempt, AutoSendScript.IsOk(result)))
                {
                    case SendDone:
                        if (send) await Task.Delay(AutoSendPolicy.SettleMs, cts.Token);
                        return true;
                    case SendRetry retry:
                        await Task.Delay(retry.DelayMs, cts.Token);
                        break;
                    default:
                        return false;
                }
            }
        }
        catch (OperationCanceledException)
        {
            return false;
        }
        catch (Exception)
        {
            return false;
        }
    }
}
