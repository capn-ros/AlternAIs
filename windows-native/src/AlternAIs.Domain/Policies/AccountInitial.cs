namespace AlternAIs.Domain.Policies;

/// <summary>La letra o número que se ve dentro del círculo de la cuenta.</summary>
public static class AccountInitial
{
    /// <summary>Primera letra o cifra del nombre, en mayúscula; «•» si no hay ninguna.</summary>
    public static string Of(string name)
    {
        foreach (var c in name.Trim())
        {
            if (char.IsLetterOrDigit(c)) return char.ToUpperInvariant(c).ToString();
        }
        return "•";
    }
}
