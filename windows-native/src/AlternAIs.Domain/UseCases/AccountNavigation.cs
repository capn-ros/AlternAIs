namespace AlternAIs.Domain.UseCases;

/// <summary>Reglas para moverse entre cuentas. Trabajan con la lista de ids en el orden en que se muestran.</summary>
public static class AccountNavigation
{
    /// <summary>
    /// Cuenta siguiente (+1) o anterior (-1), dando la vuelta en los extremos. Null si hay menos de dos
    /// cuentas. Si la actual no está en la lista, se parte desde la primera.
    /// </summary>
    public static string? Step(IReadOnlyList<string> ids, string? currentId, int delta)
    {
        if (ids.Count < 2) return null;
        var idx = Math.Max(IndexOf(ids, currentId), 0);
        var next = ((idx + delta) % ids.Count + ids.Count) % ids.Count;
        return ids[next];
    }

    /// <summary>
    /// Sentido de la animación al cambiar de cuenta: 1 si la nueva está más a la derecha, -1 si más a la
    /// izquierda, 0 si no hay cuenta anterior (primera selección) o no cambia.
    /// </summary>
    public static int Direction(IReadOnlyList<string> ids, string? fromId, string toId)
    {
        var from = IndexOf(ids, fromId);
        if (from < 0) return 0;
        return Math.Sign(IndexOf(ids, toId) - from);
    }

    private static int IndexOf(IReadOnlyList<string> ids, string? id)
    {
        if (id is null) return -1;
        for (var i = 0; i < ids.Count; i++)
        {
            if (ids[i] == id) return i;
        }
        return -1;
    }
}
