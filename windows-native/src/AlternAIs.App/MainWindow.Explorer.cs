using System.Diagnostics;
using AlternAIs.App.Platform;
using AlternAIs.App.Ui;
using AlternAIs.Domain.UseCases;
using AlternAIs.Infrastructure;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Controls.Primitives;
using Microsoft.UI.Xaml.Input;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Media.Imaging;
using Windows.System;

namespace AlternAIs.App;

/// <summary>
/// Explorador de archivos interno (cajón, Ctrl+E), como el de Electron y Android: lugares rápidos, entrar en
/// carpetas, abrir/ejecutar, seleccionar varios, copiar, mover, renombrar, borrar (a la Papelera), carpeta
/// nueva y extraer .zip. Las operaciones están en <see cref="FileOps"/> (probadas); acá solo la pantalla.
/// Atajos: Supr, F2, Enter, Retroceso, Ctrl+C/X/V, Ctrl+A, Ctrl+Mayús+N.
/// </summary>
public sealed partial class MainWindow
{
    private const int MaxThumbnails = 150;

    private UIElement? _fxPanel;
    private ListView? _fxList;
    private bool _fxSyncing;
    private readonly TextBlock _fxPath = new() { FontSize = 12, Opacity = 0.7, TextTrimming = TextTrimming.CharacterEllipsis };
    private readonly TextBlock _fxStatus = new() { FontSize = 12, TextWrapping = TextWrapping.Wrap, Margin = new Thickness(12, 0, 12, 4) };
    private readonly Button _fxUp = new();
    private readonly Button _fxPaste = new();
    private readonly DropDownButton _fxSortButton = new();
    private string _fxDir = FileOps.StartFolder();
    private FileSort _fxSort = FileSort.Name;
    private List<string> _fxClip = [];
    private bool _fxClipCut;
    private int _fxStatusToken;

    // Dentro de un .zip/.rar: el archivo abierto, la carpeta interna ("" = raíz) y su listado completo (null = hay que leerlo).
    private string? _fxArchive;
    private string _fxInner = "";
    private IReadOnlyList<ArchiveItem>? _fxArchiveItems;
    private static readonly string FxTemp = Path.Combine(Path.GetTempPath(), "AlternAIs", "archive_tmp");
    private bool ArchiveWritable => _fxArchive is not null && ArchiveRules.IsWritable(_fxArchive);

    private static readonly Dictionary<FileType, (string Glyph, Windows.UI.Color Color)> FileLook = new()
    {
        [FileType.Folder] = ("", Palette.Rgb(0xF2, 0xB0, 0x1E)),
        [FileType.Archive] = ("", Palette.Rgb(0xA0, 0x6B, 0xE8)),
        [FileType.Image] = ("", Palette.Rgb(0x2F, 0xBF, 0x71)),
        [FileType.Video] = ("", Palette.Rgb(0xEF, 0x6B, 0x3B)),
        [FileType.Audio] = ("", Palette.Rgb(0xE8, 0x5D, 0xA8)),
        [FileType.Pdf] = ("", Palette.Rgb(0xE5, 0x48, 0x4D)),
        [FileType.Doc] = ("", Palette.Rgb(0x4C, 0x8D, 0xFF)),
        [FileType.Sheet] = ("", Palette.Rgb(0x3C, 0xB7, 0xA8)),
        [FileType.Code] = ("", Palette.Rgb(0x22, 0xB8, 0xCF)),
        [FileType.Exe] = ("", Palette.Rgb(0x7B, 0xC0, 0x43)),
        [FileType.Text] = ("", Palette.Rgb(0x9A, 0xA3, 0xAE)),
        [FileType.Other] = ("", Palette.Rgb(0x8A, 0x8F, 0x98))
    };

    private static Button IconButton(string glyph, string tip, Action click)
    {
        var b = new Button
        {
            Content = AccentIcons.Create(glyph, 15), Width = 36, Height = 36, Padding = new Thickness(0),
            Background = new SolidColorBrush(Microsoft.UI.Colors.Transparent), BorderThickness = new Thickness(0)
        };
        ToolTipService.SetToolTip(b, tip);
        Microsoft.UI.Xaml.Automation.AutomationProperties.SetName(b, tip);
        b.Click += (_, _) => click();
        return b;
    }

    private UIElement EnsureExplorer(Palette pal)
    {
        if (_fxPanel is not null) return _fxPanel;
        try { if (Directory.Exists(FxTemp)) Directory.Delete(FxTemp, recursive: true); } catch (Exception) { /* en uso: no importa */ }

        // Cabecera: subir · «Archivos» + ruta · abrir en el Explorador · cerrar
        _fxUp.Content = AccentIcons.Create("", 15);
        _fxUp.Width = 36; _fxUp.Height = 36; _fxUp.Padding = new Thickness(0);
        _fxUp.Background = new SolidColorBrush(Microsoft.UI.Colors.Transparent);
        _fxUp.BorderThickness = new Thickness(0);
        ToolTipService.SetToolTip(_fxUp, "Subir de carpeta (Retroceso)");
        _fxUp.Click += (_, _) => ExplorerUp();
        var titles = new StackPanel { VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(6, 0, 6, 0) };
        titles.Children.Add(new TextBlock { Text = "Archivos", FontSize = 17, FontWeight = Microsoft.UI.Text.FontWeights.SemiBold });
        titles.Children.Add(_fxPath);
        var reveal = IconButton("", "Abrir esta carpeta en el Explorador de Windows", () => Shell(_fxDir));
        var close = IconButton("", "Cerrar (Esc)", () => TogglePanel(SidePanel.Files));
        var head = new Grid { Margin = new Thickness(8, 10, 8, 4) };
        head.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        head.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        head.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        head.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        Grid.SetColumn(_fxUp, 0); Grid.SetColumn(titles, 1); Grid.SetColumn(reveal, 2); Grid.SetColumn(close, 3);
        head.Children.Add(_fxUp); head.Children.Add(titles); head.Children.Add(reveal); head.Children.Add(close);

        // Lugares rápidos
        var places = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(12, 0, 12, 4) };
        foreach (var place in FileOps.Places())
        {
            var target = place.Path;
            places.Children.Add(Small(place.Label, () => ExplorerGo(target)));
        }
        var placesScroll = new ScrollViewer
        {
            Content = places, HorizontalScrollBarVisibility = ScrollBarVisibility.Auto,
            VerticalScrollBarVisibility = ScrollBarVisibility.Disabled, HorizontalScrollMode = ScrollMode.Auto
        };

        // Barra: pegar · carpeta nueva · orden · actualizar
        _fxPaste.Padding = new Thickness(10, 4, 10, 4);
        _fxPaste.Margin = new Thickness(0, 0, 6, 6);
        _fxPaste.FontSize = 12;
        _fxPaste.Click += (_, _) => _ = ExplorerPasteAsync();
        var sortMenu = new MenuFlyout();
        foreach (var sort in Enum.GetValues<FileSort>())
        {
            var s = sort;
            var item = new MenuFlyoutItem { Text = FileOrder.Label(s) };
            item.Click += (_, _) => { _fxSort = s; RefreshExplorer(); };
            sortMenu.Items.Add(item);
        }
        _fxSortButton.Flyout = sortMenu;
        _fxSortButton.FontSize = 12;
        _fxSortButton.Padding = new Thickness(10, 4, 10, 4);
        _fxSortButton.Margin = new Thickness(0, 0, 6, 6);
        var bar = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(12, 0, 12, 0) };
        bar.Children.Add(_fxPaste);
        bar.Children.Add(Small("Carpeta nueva", () => _ = ExplorerNewFolderAsync()));
        bar.Children.Add(_fxSortButton);
        bar.Children.Add(Small("Actualizar", RefreshExplorer));

        var list = new ListView
        {
            SelectionMode = ListViewSelectionMode.Extended, IsItemClickEnabled = false, Margin = new Thickness(4, 0, 4, 8)
        };
        list.KeyDown += ExplorerKeyDown;
        // La casilla de cada fila sigue a la selección de la lista (clic, Ctrl, Mayús, Ctrl+A).
        list.SelectionChanged += (_, _) =>
        {
            _fxSyncing = true;
            foreach (var item in list.Items.OfType<Grid>())
                if (item.Children.OfType<CheckBox>().FirstOrDefault() is { } cb)
                    cb.IsChecked = list.SelectedItems.Contains(item);
            _fxSyncing = false;
        };
        void Key(VirtualKey key, VirtualKeyModifiers mod, Action action)
        {
            var acc = new KeyboardAccelerator { Key = key, Modifiers = mod };
            acc.Invoked += (_, e) => { action(); e.Handled = true; };
            list.KeyboardAccelerators.Add(acc);
        }
        Key(VirtualKey.C, VirtualKeyModifiers.Control, () => ExplorerSetClip(cut: false));
        Key(VirtualKey.X, VirtualKeyModifiers.Control, () => ExplorerSetClip(cut: true));
        Key(VirtualKey.V, VirtualKeyModifiers.Control, () => _ = ExplorerPasteAsync());
        Key(VirtualKey.N, VirtualKeyModifiers.Control | VirtualKeyModifiers.Shift, () => _ = ExplorerNewFolderAsync());
        _fxList = list;

        var grid = new Grid();
        for (var i = 0; i < 4; i++) grid.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        grid.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
        Grid.SetRow(head, 0); Grid.SetRow(placesScroll, 1); Grid.SetRow(bar, 2); Grid.SetRow(_fxStatus, 3); Grid.SetRow(list, 4);
        grid.Children.Add(head); grid.Children.Add(placesScroll); grid.Children.Add(bar); grid.Children.Add(_fxStatus); grid.Children.Add(list);
        _fxPanel = grid;
        return grid;
    }

    // ---------------------------------------------------------------- Listado

    private void ExplorerGo(string dir)
    {
        _fxArchive = null;
        _fxInner = "";
        _fxArchiveItems = null;
        _fxDir = dir;
        RefreshExplorer();
    }

    private void OpenArchive(string path)
    {
        _fxArchive = path;
        _fxInner = "";
        _fxArchiveItems = null;
        if (!ArchiveRules.IsWritable(path)) ExplorerStatus(ArchiveRules.RarNote);
        RefreshExplorer();
    }

    private void ExplorerUp()
    {
        if (_fxArchive is not null)
        {
            if (_fxInner.Length == 0)
            {
                _fxArchive = null;
                _fxArchiveItems = null;
            }
            else
            {
                _fxInner = ArchiveRules.ParentInner(_fxInner);
            }
            RefreshExplorer();
            return;
        }
        var parent = FileOps.Parent(_fxDir);
        if (parent is not null) ExplorerGo(parent);
    }

    private void RefreshExplorer()
    {
        if (_fxList is null) return;
        if (_fxArchive is not null)
        {
            _ = RefreshArchiveAsync();
            return;
        }
        IReadOnlyList<FileEntry> entries;
        try
        {
            entries = FileOrder.Sort(FileOps.List(_fxDir), _fxSort);
        }
        catch (FileOpException ex)
        {
            ExplorerStatus(ex.Message);
            var fallback = FileOps.StartFolder();
            if (!string.Equals(_fxDir, fallback, StringComparison.OrdinalIgnoreCase))
            {
                _fxDir = fallback;
                RefreshExplorer();
            }
            return;
        }
        _fxPath.Text = _fxDir;
        ToolTipService.SetToolTip(_fxPath, _fxDir);
        _fxUp.IsEnabled = FileOps.Parent(_fxDir) is not null;
        _fxSortButton.Content = "Orden: " + FileOrder.Label(_fxSort);
        _fxPaste.Content = _fxClip.Count > 0 ? (_fxClipCut ? "Mover acá (" : "Pegar (") + _fxClip.Count + ")" : "Pegar";
        _fxPaste.IsEnabled = _fxClip.Count > 0;

        FillExplorerList(entries, allowThumbnails: true);
        if (entries.Count == 0) ExplorerStatus("Carpeta vacía");
    }

    private void FillExplorerList(IReadOnlyList<FileEntry> entries, bool allowThumbnails)
    {
        if (_fxList is null) return;
        _fxList.Items.Clear();
        var thumbs = 0;
        foreach (var e in entries)
        {
            var thumb = allowThumbnails && !e.IsDir && thumbs < MaxThumbnails && FileTypes.HasThumbnail(e.Name);
            if (thumb) thumbs++;
            _fxList.Items.Add(ExplorerRow(e, thumb));
        }
    }

    /// <summary>Muestra lo que hay dentro del .zip/.rar abierto (lo lee una sola vez, fuera del hilo de la pantalla).</summary>
    private async Task RefreshArchiveAsync()
    {
        var arc = _fxArchive;
        if (arc is null || _fxList is null) return;
        if (_fxArchiveItems is null)
        {
            try
            {
                var items = await Task.Run(() => ArchiveOps.List(arc));
                if (_fxArchive != arc) return;
                _fxArchiveItems = items;
            }
            catch (Exception ex)
            {
                ExplorerStatus("No se pudo abrir «" + Path.GetFileName(arc) + "»: " + ex.Message);
                _fxArchive = null;
                _fxInner = "";
                RefreshExplorer();
                return;
            }
        }
        var entries = FileOrder.Sort(
            ArchiveRules.Children(_fxArchiveItems, _fxInner)
                .Select(i => new FileEntry(i.Path[(i.Path.LastIndexOf('/') + 1)..], ArchiveRules.VirtualPath(arc, i.Path), i.IsDir, i.Size, i.ModifiedMs)),
            _fxSort);
        var shown = ArchiveRules.ShownPath(Path.GetFileName(arc), _fxInner);
        _fxPath.Text = shown;
        ToolTipService.SetToolTip(_fxPath, shown);
        _fxUp.IsEnabled = true;
        _fxSortButton.Content = "Orden: " + FileOrder.Label(_fxSort);
        _fxPaste.Content = _fxClip.Count > 0 ? "Pegar aquí (" + _fxClip.Count + ")" : "Pegar";
        _fxPaste.IsEnabled = _fxClip.Count > 0 && ArchiveWritable;
        FillExplorerList(entries, allowThumbnails: false);
        if (entries.Count == 0) ExplorerStatus("Carpeta vacía");
    }

    private FrameworkElement ExplorerRow(FileEntry e, bool thumbnail)
    {
        var (glyph, color) = FileLook[e.Type];
        FrameworkElement icon = new FontIcon { Glyph = glyph, FontSize = 20, Foreground = Palette.Brush(color) };
        if (thumbnail)
        {
            try
            {
                icon = new Image
                {
                    Width = 32, Height = 32, Stretch = Stretch.UniformToFill,
                    Source = new BitmapImage { DecodePixelWidth = 64, UriSource = new Uri(e.FullPath) }
                };
            }
            catch (Exception)
            {
                // queda el ícono
            }
        }
        var iconBox = new Border { Width = 36, Height = 36, Child = icon, CornerRadius = new CornerRadius(6) };

        var modified = DateTimeOffset.FromUnixTimeMilliseconds(e.ModifiedMs).ToLocalTime();
        var meta = (e.IsDir ? "Carpeta" : SizeFormat.Human(e.Size)) + " · " + modified.ToString("dd/MM/yyyy HH:mm");
        var texts = new StackPanel { VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(10, 0, 0, 0) };
        texts.Children.Add(new TextBlock { Text = e.Name, TextTrimming = TextTrimming.CharacterEllipsis });
        texts.Children.Add(new TextBlock { Text = meta, FontSize = 11, Opacity = 0.65 });

        var row = new Grid { Tag = e, Background = new SolidColorBrush(Microsoft.UI.Colors.Transparent), Padding = new Thickness(2, 3, 2, 3) };
        row.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        row.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        Grid.SetColumn(iconBox, 0);
        Grid.SetColumn(texts, 1);
        row.Children.Add(iconBox);
        row.Children.Add(texts);
        // Casilla a la derecha para seleccionar varios archivos a la vez.
        var check = new CheckBox
        {
            MinWidth = 0, Width = 32, Padding = new Thickness(0), Margin = new Thickness(4, 0, 4, 0),
            VerticalAlignment = VerticalAlignment.Center
        };
        check.Click += (_, _) =>
        {
            if (_fxSyncing || _fxList is null) return;
            if (check.IsChecked == true) { if (!_fxList.SelectedItems.Contains(row)) _fxList.SelectedItems.Add(row); }
            else _fxList.SelectedItems.Remove(row);
        };
        Grid.SetColumn(check, 2);
        row.Children.Add(check);
        ToolTipService.SetToolTip(row, e.Name);
        row.DoubleTapped += (_, _) => ExplorerOpen(e);
        row.RightTapped += (_, args) =>
        {
            if (_fxList is null) return;
            if (!_fxList.SelectedItems.Contains(row))
            {
                _fxList.SelectedItems.Clear();
                _fxList.SelectedItem = row;
            }
            ExplorerMenu().ShowAt(row, new FlyoutShowOptions { Position = args.GetPosition(row) });
            args.Handled = true;
        };
        return row;
    }

    private List<FileEntry> ExplorerSelection() =>
        _fxList is null ? [] : _fxList.SelectedItems.OfType<FrameworkElement>().Select(x => x.Tag).OfType<FileEntry>().ToList();

    private void ExplorerStatus(string text)
    {
        _fxStatus.Text = text;
        var token = ++_fxStatusToken;
        DispatcherQueue.TryEnqueue(async () =>
        {
            await Task.Delay(6000);
            if (token == _fxStatusToken) _fxStatus.Text = "";
        });
    }

    // ---------------------------------------------------------------- Acciones

    private void ExplorerKeyDown(object sender, KeyRoutedEventArgs e)
    {
        switch (e.Key)
        {
            case VirtualKey.Delete: _ = ExplorerDeleteAsync(); e.Handled = true; break;
            case VirtualKey.F2: _ = ExplorerRenameAsync(); e.Handled = true; break;
            case VirtualKey.Back: ExplorerUp(); e.Handled = true; break;
            case VirtualKey.Enter:
                if (ExplorerSelection().FirstOrDefault() is { } first) ExplorerOpen(first);
                e.Handled = true;
                break;
        }
    }

    private MenuFlyout ExplorerMenu()
    {
        var sel = ExplorerSelection();
        var menu = new MenuFlyout();
        void Item(string text, string glyph, Action action)
        {
            var item = new MenuFlyoutItem { Text = text, Icon = AccentIcons.Create(glyph) };
            item.Click += (_, _) => action();
            menu.Items.Add(item);
        }
        if (sel.Count == 0) return menu;
        var multi = sel.Count > 1;
        menu.Items.Add(new MenuFlyoutItem { Text = multi ? sel.Count + " elementos" : sel[0].Name, IsEnabled = false });
        menu.Items.Add(new MenuFlyoutSeparator());
        if (_fxArchive is not null)
        {
            // Dentro de un .zip/.rar: copiar hacia afuera y extraer siempre; modificar solo en .zip.
            if (!multi)
            {
                var inside = sel[0];
                Item(inside.IsDir ? "Abrir carpeta" : "Abrir", "\uE8E5", () => ExplorerOpen(inside));
            }
            Item("Copiar", "\uE8C8", () => ExplorerSetClip(cut: false));
            Item("Extraer en una carpeta junto al archivo", "\uE7B8", () => _ = ExplorerExtractEntriesAsync(sel));
            if (ArchiveWritable)
            {
                if (!multi) Item("Renombrar", "\uE8AC", () => _ = ExplorerRenameAsync());
                Item("Quitar del archivo comprimido", "\uE74D", () => _ = ExplorerDeleteAsync());
            }
            return menu;
        }
        if (!multi)
        {
            var e = sel[0];
            var isArchive = !e.IsDir && ArchiveRules.IsArchive(e.Name);
            if (isArchive) Item("Ver lo que tiene adentro", "\uE8E5", () => OpenArchive(e.FullPath));
            Item(e.IsDir ? "Abrir carpeta" : isArchive ? "Abrir con la app predeterminada" : e.Type == FileType.Exe ? "Ejecutar" : "Abrir", "\uE8E5",
                () => { if (isArchive) Shell(e.FullPath); else ExplorerOpen(e); });
            Item("Mostrar en el Explorador", "\uE838", () => Reveal(e.FullPath));
            if (isArchive)
            {
                Item("Extraer en carpeta", "\uE7B8", () => _ = ExplorerExtractAsync(e, intoFolder: true));
                Item("Extraer aquí", "\uE7B8", () => _ = ExplorerExtractAsync(e, intoFolder: false));
            }
        }
        Item("Copiar", "\uE8C8", () => ExplorerSetClip(cut: false));
        Item("Mover", "\uE8C6", () => ExplorerSetClip(cut: true));
        if (!multi) Item("Renombrar", "\uE8AC", () => _ = ExplorerRenameAsync());
        Item("Borrar (a la Papelera)", "\uE74D", () => _ = ExplorerDeleteAsync());
        return menu;
    }

    private void ExplorerOpen(FileEntry e)
    {
        if (_fxArchive is not null)
        {
            if (e.IsDir)
            {
                _fxInner = ArchiveRules.InnerOf(_fxArchive, e.FullPath) ?? _fxInner;
                RefreshExplorer();
            }
            else if (ArchiveRules.InnerOf(_fxArchive, e.FullPath) is { } inner)
            {
                _ = ExplorerOpenFromArchiveAsync(_fxArchive, inner);
            }
            return;
        }
        if (e.IsDir)
        {
            ExplorerGo(e.FullPath);
            return;
        }
        // Un .zip/.rar se abre por dentro; «Abrir con la app predeterminada» está en el menú.
        if (ArchiveRules.IsArchive(e.Name))
        {
            OpenArchive(e.FullPath);
            return;
        }
        Shell(e.FullPath);
    }

    /// <summary>Saca un archivo de adentro del .zip/.rar a una carpeta temporal y lo abre con la app predeterminada.</summary>
    private async Task ExplorerOpenFromArchiveAsync(string archive, string inner)
    {
        try
        {
            var path = await Task.Run(() => ArchiveOps.ExtractOne(archive, inner, FxTemp));
            Shell(path);
        }
        catch (Exception ex)
        {
            ExplorerStatus("No se pudo abrir: " + ex.Message);
        }
    }

    /// <summary>Abre con la app predeterminada de Windows (ejecuta .exe/.bat, como «shell.openPath» de Electron).</summary>
    private void Shell(string path)
    {
        try
        {
            Process.Start(new ProcessStartInfo { FileName = path, UseShellExecute = true, WorkingDirectory = Path.GetDirectoryName(path) ?? "" });
        }
        catch (Exception ex)
        {
            ExplorerStatus("No se pudo abrir: " + ex.Message);
        }
    }

    private void Reveal(string path)
    {
        try { Process.Start(new ProcessStartInfo { FileName = "explorer.exe", Arguments = "/select,\"" + path + "\"", UseShellExecute = true }); }
        catch (Exception ex) { ExplorerStatus("No se pudo abrir el Explorador: " + ex.Message); }
    }

    private void ExplorerSetClip(bool cut)
    {
        var sel = ExplorerSelection();
        if (sel.Count == 0) return;
        if (_fxArchive is not null)
        {
            // Desde adentro del .zip/.rar solo se copia: se extrae a la carpeta temporal y eso queda en el portapapeles.
            _ = ExplorerCopyOutAsync(_fxArchive, sel);
            return;
        }
        _fxClip = sel.Select(x => x.FullPath).ToList();
        _fxClipCut = cut;
        ExplorerStatus((cut ? "Para mover: " : "Para copiar: ") + sel.Count + ". Andá a la carpeta de destino y tocá «" + (cut ? "Mover acá" : "Pegar") + "» (Ctrl+V).");
        RefreshExplorer();
    }

    private async Task ExplorerCopyOutAsync(string archive, List<FileEntry> sel)
    {
        var inners = sel.Select(x => ArchiveRules.InnerOf(archive, x.FullPath)).OfType<string>().ToList();
        var dest = Path.Combine(FxTemp, "copia" + Guid.NewGuid().ToString("N"));
        var ok = await ExplorerRun(() =>
        {
            ArchiveOps.Extract(archive, inners, dest);
            return ArchiveRules.CopiedOut(inners.Count);
        });
        if (!ok) return;
        _fxClip = Directory.Exists(dest) ? Directory.GetFileSystemEntries(dest).ToList() : [];
        _fxClipCut = false;
        RefreshExplorer();
    }

    /// <summary>Corre una operación de archivos fuera del hilo de la pantalla y traduce los errores a un mensaje.</summary>
    private async Task<bool> ExplorerRun(Func<string> work)
    {
        try
        {
            var message = await Task.Run(work);
            ExplorerStatus(message);
            return true;
        }
        catch (FileOpException ex) { ExplorerStatus(ex.Message); }
        catch (UnauthorizedAccessException) { ExplorerStatus("Sin permiso para esa operación"); }
        catch (IOException ex) { ExplorerStatus("No se pudo (¿está en uso?): " + ex.Message); }
        catch (Exception ex) { ExplorerStatus("No se pudo: " + ex.Message); }
        finally { RefreshExplorer(); }
        return false;
    }

    private async Task ExplorerPasteAsync()
    {
        if (_fxClip.Count == 0) return;
        var items = _fxClip.ToList();
        var cut = _fxClipCut;
        var dest = _fxDir;
        if (cut) _fxClip = [];
        if (_fxArchive is { } arc)
        {
            if (!ArchiveWritable) { ExplorerStatus(ArchiveRules.RarNote); return; }
            var inner = _fxInner;
            _fxArchiveItems = null;
            await ExplorerRun(() =>
            {
                var n = ArchiveOps.Add(arc, inner, items);
                if (cut)
                {
                    foreach (var p in items)
                    {
                        if (Directory.Exists(p)) Directory.Delete(p, recursive: true);
                        else if (File.Exists(p)) File.Delete(p);
                    }
                }
                return ArchiveRules.Added(n);
            });
            return;
        }
        await ExplorerRun(() =>
        {
            foreach (var p in items)
            {
                if (cut) FileOps.MoveInto(p, dest);
                else FileOps.CopyInto(p, dest);
            }
            return (cut ? "Movidos: " : "Copiados: ") + items.Count;
        });
    }

    private async Task ExplorerNewFolderAsync()
    {
        var name = await Dialogs.Text("Carpeta nueva", "Carpeta nueva", "Crear");
        if (name is null) return;
        if (_fxArchive is { } arc)
        {
            if (!ArchiveWritable) { ExplorerStatus(ArchiveRules.RarNote); return; }
            var inner = _fxInner;
            _fxArchiveItems = null;
            await ExplorerRun(() => "Carpeta «" + ArchiveOps.CreateFolder(arc, inner, name) + "» creada");
            return;
        }
        var dir = _fxDir;
        await ExplorerRun(() => "Carpeta «" + Path.GetFileName(FileOps.CreateFolder(dir, name)) + "» creada");
    }

    private async Task ExplorerRenameAsync()
    {
        var sel = ExplorerSelection();
        if (sel.Count != 1) return;
        var e = sel[0];
        if (_fxArchive is { } arc)
        {
            if (!ArchiveWritable) { ExplorerStatus(ArchiveRules.RarNote); return; }
            var inner = ArchiveRules.InnerOf(arc, e.FullPath);
            if (inner is null) return;
            var newName = await Dialogs.Text("Renombrar", e.Name, "Renombrar");
            if (newName is null || newName == e.Name) return;
            _fxArchiveItems = null;
            await ExplorerRun(() => "Ahora se llama «" + ArchiveOps.Rename(arc, inner, newName) + "»");
            return;
        }
        var name = await Dialogs.Text("Renombrar", e.Name, "Renombrar");
        if (name is null || name == e.Name) return;
        await ExplorerRun(() => "Ahora se llama «" + Path.GetFileName(FileOps.Rename(e.FullPath, name)) + "»");
    }

    private async Task ExplorerDeleteAsync()
    {
        var sel = ExplorerSelection();
        if (sel.Count == 0) return;
        var what = sel.Count == 1 ? "«" + sel[0].Name + "»" : sel.Count + " elementos";
        if (_fxArchive is { } arc)
        {
            if (!ArchiveWritable) { ExplorerStatus(ArchiveRules.RarNote); return; }
            if (!await Dialogs.Confirm("Quitar", "¿Quitar " + what + " del archivo comprimido? No va a la Papelera: se borra de adentro del .zip.", "Quitar")) return;
            var inners = sel.Select(x => ArchiveRules.InnerOf(arc, x.FullPath)).OfType<string>().ToList();
            _fxArchiveItems = null;
            await ExplorerRun(() => ArchiveRules.Removed(ArchiveOps.Delete(arc, inners)));
            return;
        }
        if (!await Dialogs.Confirm("Borrar", "¿Mandar " + what + " a la Papelera de reciclaje? Se pueden recuperar desde ahí.", "Borrar")) return;
        var done = 0;
        string? error = null;
        foreach (var e in sel)
        {
            try
            {
                await RecycleBin.SendAsync(e.FullPath);
                done++;
            }
            catch (Exception ex)
            {
                error = ex.Message;
            }
        }
        RefreshExplorer();
        ExplorerStatus(error is null ? "Enviados a la Papelera: " + done : "Enviados a la Papelera: " + done + ". Alguno no se pudo: " + error);
    }

    private async Task ExplorerExtractAsync(FileEntry zip, bool intoFolder)
    {
        var dest = intoFolder ? FileOps.ExtractFolderFor(zip.FullPath) : _fxDir;
        await ExplorerRun(() =>
        {
            var n = ArchiveRules.IsRar(zip.Name) ? ArchiveOps.Extract(zip.FullPath, [], dest) : FileOps.Extract(zip.FullPath, dest);
            return "Extraídos " + n + " archivos en «" + Path.GetFileName(dest.TrimEnd(Path.DirectorySeparatorChar)) + "»";
        });
    }

    /// <summary>Dentro de un .zip/.rar: extrae lo elegido en una carpeta junto al archivo.</summary>
    private async Task ExplorerExtractEntriesAsync(List<FileEntry> sel)
    {
        if (_fxArchive is not { } arc) return;
        var inners = sel.Select(x => ArchiveRules.InnerOf(arc, x.FullPath)).OfType<string>().ToList();
        var dest = FileOps.ExtractFolderFor(arc);
        await ExplorerRun(() =>
        {
            var n = ArchiveOps.Extract(arc, inners, dest);
            return "Extraídos " + n + " archivos en «" + Path.GetFileName(dest.TrimEnd(Path.DirectorySeparatorChar)) + "»";
        });
    }
}
