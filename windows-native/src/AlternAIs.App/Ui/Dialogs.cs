using AlternAIs.Domain;
using AlternAIs.Domain.Models;
using AlternAIs.Domain.UseCases;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;

namespace AlternAIs.App.Ui;

/// <summary>Diálogos de la app (se arman en código). Todos devuelven null/false si se cancelan.</summary>
internal static class Dialogs
{
    public static XamlRoot? Root { get; set; }
    public static ElementTheme Theme { get; set; } = ElementTheme.Dark;

    private static readonly SemaphoreSlim OneAtATime = new(1, 1);

    /// <summary>WinUI deja abrir un solo ContentDialog a la vez: si ya hay uno, este espera su turno.</summary>
    public static async Task<ContentDialogResult> Show(ContentDialog d)
    {
        await OneAtATime.WaitAsync();
        try
        {
            d.XamlRoot ??= Root;
            return await d.ShowAsync();
        }
        finally
        {
            OneAtATime.Release();
        }
    }

    private static ContentDialog Make(string title, string primary) => new()
    {
        Title = title,
        XamlRoot = Root,
        RequestedTheme = Theme,
        PrimaryButtonText = primary,
        CloseButtonText = "Cancelar",
        DefaultButton = ContentDialogButton.Primary
    };

    public static async Task Message(string title, string text)
    {
        var d = new ContentDialog
        {
            Title = title, Content = new TextBlock { Text = text, TextWrapping = TextWrapping.Wrap },
            CloseButtonText = "Aceptar", XamlRoot = Root, RequestedTheme = Theme
        };
        await Show(d);
    }

    public static async Task<bool> Confirm(string title, string text, string okText)
    {
        var d = Make(title, okText);
        d.Content = new TextBlock { Text = text, TextWrapping = TextWrapping.Wrap };
        return await Show(d) == ContentDialogResult.Primary;
    }

    /// <summary>Un texto (una línea o varias). Devuelve null si se cancela.</summary>
    public static async Task<string?> Text(string title, string initial, string okText, bool multiline = false)
    {
        var box = new TextBox
        {
            Text = initial, AcceptsReturn = multiline, TextWrapping = multiline ? TextWrapping.Wrap : TextWrapping.NoWrap,
            MinHeight = multiline ? 120 : 0, MaxLength = multiline ? Snippets.MaxLength : 0
        };
        var d = Make(title, okText);
        d.Content = box;
        d.Opened += (_, _) => box.Focus(FocusState.Programmatic);
        return await Show(d) == ContentDialogResult.Primary ? box.Text : null;
    }

    /// <summary>
    /// «Respuesta automática» al marcar el límite: ninguna, un mensaje guardado o un texto propio. Devuelve el texto
    /// ("" = ninguna) o null si se cancela.
    /// </summary>
    public static async Task<string?> AutoReply(IReadOnlyList<string> snippets, string current)
    {
        var labels = new List<string> { "Ninguna" };
        labels.AddRange(snippets.Select(s => s.Length > 70 ? s[..70] + "…" : s));
        labels.Add("Texto propio…");
        var ownIndex = labels.Count - 1;
        var options = new RadioButtons { ItemsSource = labels };
        var own = new TextBox
        {
            AcceptsReturn = true, TextWrapping = TextWrapping.Wrap, MinHeight = 90, MaxLength = Snippets.MaxLength,
            PlaceholderText = "Escribí el mensaje", Visibility = Visibility.Collapsed, Margin = new Thickness(0, 8, 0, 0)
        };
        var found = snippets.ToList().IndexOf(current);
        var matched = current.Length == 0 ? 0 : found >= 0 ? found + 1 : ownIndex;
        options.SelectedIndex = matched;
        if (matched == ownIndex)
        {
            own.Text = current;
            own.Visibility = Visibility.Visible;
        }
        options.SelectionChanged += (_, _) =>
        {
            own.Visibility = options.SelectedIndex == ownIndex ? Visibility.Visible : Visibility.Collapsed;
            if (own.Visibility == Visibility.Visible) own.Focus(FocusState.Programmatic);
        };
        var d = Make("Respuesta automática", "Guardar");
        d.Content = new StackPanel
        {
            Children =
            {
                new TextBlock
                {
                    Text = "Cuando termine el descanso, AlternAIs escribe y envía este mensaje en la cuenta (también con la ventana en la bandeja).",
                    TextWrapping = TextWrapping.Wrap, Opacity = 0.8, Margin = new Thickness(0, 0, 0, 8)
                },
                new ScrollViewer { Content = options, MaxHeight = 280 },
                own
            }
        };
        if (await Show(d) != ContentDialogResult.Primary) return null;
        var index = options.SelectedIndex;
        if (index <= 0) return "";
        if (index == ownIndex) return own.Text.Trim();
        return snippets[index - 1];
    }

    /// <summary>Formulario de cuenta nueva o editada (reglas de <see cref="ValidateAccountForm"/>).</summary>
    public static async Task<AccountFormValid?> AccountForm(Account? existing, int defaultColor)
    {
        var name = new TextBox { Header = "Nombre (ej. Trabajo, Estudio)", Text = existing?.Name ?? "", MaxLength = Account.MaxName };
        var purpose = new TextBox { Header = "¿Para qué la usás? (opcional)", Text = existing?.Purpose ?? "", MaxLength = Account.MaxPurpose, Margin = new Thickness(0, 12, 0, 0) };
        var email = new TextBox { Header = "Correo de la cuenta (opcional)", Text = existing?.Email ?? "", MaxLength = AlternAIs.Domain.Policies.EmailPolicy.MaxLength, Margin = new Thickness(0, 12, 0, 0) };
        var error = new TextBlock { Foreground = Palette.Brush(Palette.Rgb(0xD6, 0x45, 0x45)), Visibility = Visibility.Collapsed };
        var color = existing?.Color ?? defaultColor;

        var swatches = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 4, 0, 0) };
        void Paint()
        {
            swatches.Children.Clear();
            foreach (var c in AccountPalette.Colors)
            {
                var selected = c == color;
                var b = new Button
                {
                    Width = 34, Height = 34, CornerRadius = new CornerRadius(17), Margin = new Thickness(0, 0, 6, 0),
                    Background = Palette.Brush(Palette.FromArgb(c)), BorderThickness = new Thickness(selected ? 3 : 0),
                    BorderBrush = Palette.Brush(Palette.Rgb(255, 255, 255))
                };
                var captured = c;
                b.Click += (_, _) => { color = captured; Paint(); };
                swatches.Children.Add(b);
            }
        }
        Paint();

        var panel = new StackPanel();
        panel.Children.Add(name);
        panel.Children.Add(error);
        panel.Children.Add(purpose);
        panel.Children.Add(email);
        panel.Children.Add(new TextBlock { Text = "Color", Margin = new Thickness(0, 16, 0, 0), Opacity = 0.7 });
        panel.Children.Add(new ScrollViewer { Content = swatches, HorizontalScrollBarVisibility = ScrollBarVisibility.Auto, VerticalScrollBarVisibility = ScrollBarVisibility.Disabled, HorizontalScrollMode = ScrollMode.Auto });

        var d = Make(existing is null ? "Nueva cuenta" : "Editar cuenta", "Guardar");
        d.Content = panel;
        AccountFormValid? result = null;
        d.PrimaryButtonClick += (_, args) =>
        {
            switch (ValidateAccountForm.Invoke(name.Text, purpose.Text, email.Text, color))
            {
                case AccountFormInvalid invalid:
                    error.Text = invalid.NameError;
                    error.Visibility = Visibility.Visible;
                    args.Cancel = true;
                    break;
                case AccountFormValid valid:
                    result = valid;
                    break;
            }
        };
        d.Opened += (_, _) => name.Focus(FocusState.Programmatic);
        await Show(d);
        return result;
    }

    /// <summary>
    /// «Marcar límite»: elige cuándo termina el descanso. Devuelve el instante (epoch ms) o null si se cancela.
    /// </summary>
    public static async Task<long?> PickLimit(AiService service, IClock clock)
    {
        var options = LimitOptions.ForService(service);
        LimitOption? chosen = null;
        var d = new ContentDialog
        {
            Title = "Marcar límite", CloseButtonText = "Cancelar", XamlRoot = Root, RequestedTheme = Theme
        };
        var list = new ListView
        {
            ItemsSource = options.Select(o => o.Label).ToList(), SelectionMode = ListViewSelectionMode.None, IsItemClickEnabled = true
        };
        list.ItemClick += (_, e) =>
        {
            chosen = options[list.Items.IndexOf(e.ClickedItem)];
            d.Hide();
        };
        d.Content = new StackPanel
        {
            Children = { new TextBlock { Text = "¿Cuándo se reinicia?", Opacity = 0.7, Margin = new Thickness(0, 0, 0, 8) }, list }
        };
        await Show(d);

        var now = DateTimeOffset.FromUnixTimeMilliseconds(clock.NowMs);
        switch (chosen)
        {
            case AfterHours after:
                return after.UntilMs(clock.NowMs);
            case ExactTimeOption:
                return await ExactTime(now, clock.Zone);
            default:
                return null;
        }
    }

    private static async Task<long?> ExactTime(DateTimeOffset now, TimeZoneInfo zone)
    {
        var hour = new TextBox { Header = "Hora", Width = 80, MaxLength = 2, PlaceholderText = "17" };
        var minute = new TextBox { Header = "Minutos", Width = 80, MaxLength = 2, Text = "00", Margin = new Thickness(8, 0, 0, 0) };
        var period = new ComboBox
        {
            Header = "AM/PM", Width = 100, Margin = new Thickness(8, 0, 0, 0),
            ItemsSource = new[] { "Auto", "AM", "PM" }, SelectedIndex = 0
        };
        var preview = new TextBlock { Margin = new Thickness(0, 12, 0, 0), Opacity = 0.8 };
        DateTimeOffset? Current() => ExactTimeInput.Resolve(hour.Text, minute.Text, (DayPeriod)Math.Max(period.SelectedIndex, 0), now, zone);
        void Update() => preview.Text = hour.Text.Length == 0 ? "" : ExactTimeInput.Describe(Current(), now, zone);
        hour.TextChanged += (_, _) => { Update(); if (hour.Text.Length == 2) minute.Focus(FocusState.Programmatic); };
        minute.TextChanged += (_, _) => Update();
        period.SelectionChanged += (_, _) => Update();

        var row = new StackPanel { Orientation = Orientation.Horizontal, Children = { hour, minute, period } };
        var d = Make("Hora en que se reinicia", "Marcar");
        d.Content = new StackPanel { Children = { row, preview } };
        long? result = null;
        d.PrimaryButtonClick += (_, args) =>
        {
            var target = Current();
            if (target is null) { preview.Text = "Hora no válida"; args.Cancel = true; return; }
            result = target.Value.ToUnixTimeMilliseconds();
        };
        d.Opened += (_, _) => hour.Focus(FocusState.Programmatic);
        await Show(d);
        return result;
    }
}
