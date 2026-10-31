using Windows.Storage;

namespace AlternAIs.App.Platform;

/// <summary>Borrar desde el explorador manda a la Papelera de reciclaje (se puede recuperar), como en Electron.</summary>
internal static class RecycleBin
{
    public static async Task SendAsync(string path)
    {
        if (Directory.Exists(path))
        {
            var folder = await StorageFolder.GetFolderFromPathAsync(path);
            await folder.DeleteAsync(StorageDeleteOption.Default);
        }
        else
        {
            var file = await StorageFile.GetFileFromPathAsync(path);
            await file.DeleteAsync(StorageDeleteOption.Default);
        }
    }
}
