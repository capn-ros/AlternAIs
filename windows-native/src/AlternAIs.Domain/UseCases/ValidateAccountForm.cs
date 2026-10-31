using AlternAIs.Domain.Models;
using AlternAIs.Domain.Policies;

namespace AlternAIs.Domain.UseCases;

public abstract record AccountFormResult;

/// <summary>Datos ya limpios, listos para guardar.</summary>
public sealed record AccountFormValid(string Name, string Purpose, string Email, int Color) : AccountFormResult;

/// <summary>El nombre es lo único obligatorio.</summary>
public sealed record AccountFormInvalid(string NameError) : AccountFormResult;

/// <summary>
/// Reglas del formulario de cuenta: nombre obligatorio (máx. 24), propósito opcional (máx. 60) y
/// correo opcional (solo si tiene forma de correo; nunca contraseñas).
/// </summary>
public static class ValidateAccountForm
{
    public static AccountFormResult Invoke(string name, string purpose, string email, int color)
    {
        var cleanName = Cut(name.Trim(), Account.MaxName).Trim();
        if (cleanName.Length == 0) return new AccountFormInvalid("Poné un nombre");
        return new AccountFormValid(cleanName, Cut(purpose.Trim(), Account.MaxPurpose), EmailPolicy.Clean(email), color);
    }

    private static string Cut(string s, int max) => s.Length > max ? s[..max] : s;
}
