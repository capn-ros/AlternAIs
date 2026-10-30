using AlternAIs.Application;

namespace AlternAIs.Infrastructure;

/// <summary>Guarda el estado en un archivo JSON (escribe a un temporal y lo reemplaza, así no queda a medias).</summary>
public sealed class JsonFileStateStore(string path) : IStateStore
{
    /// <summary>%APPDATA%\Claudeck\accounts.json: la misma ruta que usaba la versión Electron.</summary>
    public static string DefaultPath() =>
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "Claudeck", "accounts.json");

    public AppState Load()
    {
        try { return File.Exists(path) ? StateJson.Parse(File.ReadAllText(path)) : AppState.Initial; }
        catch (IOException) { return AppState.Initial; }
        catch (UnauthorizedAccessException) { return AppState.Initial; }
    }

    public void Save(AppState state)
    {
        try
        {
            var dir = Path.GetDirectoryName(path);
            if (!string.IsNullOrEmpty(dir)) Directory.CreateDirectory(dir);
            var tmp = path + ".tmp";
            File.WriteAllText(tmp, StateJson.Serialize(state));
            File.Move(tmp, path, overwrite: true);
        }
        catch (IOException) { /* no se pudo guardar: la app sigue con lo que tiene en memoria */ }
        catch (UnauthorizedAccessException) { }
    }
}
