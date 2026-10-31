namespace AlternAIs.Domain.Models;

/// <summary>Reglas de los mensajes guardados (listas inmutables: cada operación devuelve una lista nueva).</summary>
public static class Snippets
{
    public const int MaxCount = 50;
    public const int MaxLength = 4000;

    /// <summary>Mensajes de ejemplo la primera vez; se pueden editar y borrar.</summary>
    public static readonly IReadOnlyList<string> Defaults =
    [
        "Seguí donde te quedaste.",
        "Antes de hacer nada, explicame lo siguiente: ",
        "Resumí lo anterior en pocos puntos.",
        "Explicámelo de forma simple, con un ejemplo."
    ];

    /// <summary>Saca los vacíos, deja a lo sumo 50 y corta cada uno a 4000 caracteres.</summary>
    public static IReadOnlyList<string> Normalize(IEnumerable<string> list) =>
        list.Where(s => !string.IsNullOrWhiteSpace(s)).Take(MaxCount)
            .Select(s => s.Length > MaxLength ? s[..MaxLength] : s).ToList();

    public static IReadOnlyList<string> Add(IReadOnlyList<string> list, string text) =>
        string.IsNullOrWhiteSpace(text) ? list : Normalize(list.Append(text));

    public static IReadOnlyList<string> Replace(IReadOnlyList<string> list, int index, string text)
    {
        if (string.IsNullOrWhiteSpace(text) || index < 0 || index >= list.Count) return list;
        var copy = list.ToList();
        copy[index] = text;
        return Normalize(copy);
    }

    /// <summary>Sube un mensaje una posición (el primero no se mueve).</summary>
    public static IReadOnlyList<string> MoveUp(IReadOnlyList<string> list, int index)
    {
        if (index < 1 || index >= list.Count) return list;
        var copy = list.ToList();
        var item = copy[index];
        copy.RemoveAt(index);
        copy.Insert(index - 1, item);
        return copy;
    }

    public static IReadOnlyList<string> Remove(IReadOnlyList<string> list, int index)
    {
        if (index < 0 || index >= list.Count) return list;
        var copy = list.ToList();
        copy.RemoveAt(index);
        return copy;
    }
}
