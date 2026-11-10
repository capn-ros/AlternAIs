using AlternAIs.Domain.UseCases;
using static AlternAIs.Domain.Tests.TestSupport;

namespace AlternAIs.Domain.Tests;

public class ResolveExactTimeTests
{
    private static DateTimeOffset? Resolve(int h, int m, DayPeriod p, DateTimeOffset now) =>
        ResolveExactTime.Invoke(h, m, p, now, Zone);

    [Fact] public void Auto_a_las_16_30_el_5_es_17_00_de_hoy() =>
        Assert.Equal(At(17), Resolve(5, 0, DayPeriod.Auto, At(16, 30)));

    [Fact] public void Auto_a_las_8_30_el_5_es_5_PM_de_hoy_porque_las_5_AM_ya_pasaron() =>
        Assert.Equal(At(17), Resolve(5, 0, DayPeriod.Auto, At(8, 30)));

    [Fact] public void Auto_a_las_3_30_el_5_es_5_AM_de_hoy() =>
        Assert.Equal(At(5), Resolve(5, 0, DayPeriod.Auto, At(3, 30)));

    [Fact] public void Auto_despues_de_las_17_el_5_pasa_a_5_AM_de_manana() =>
        Assert.Equal(At(5).AddDays(1), Resolve(5, 0, DayPeriod.Auto, At(18)));

    [Fact] public void AM_forzado_ya_pasado_va_a_manana() =>
        Assert.Equal(At(5).AddDays(1), Resolve(5, 0, DayPeriod.Am, At(9)));

    [Fact] public void PM_forzado() => Assert.Equal(At(21, 15), Resolve(9, 15, DayPeriod.Pm, At(9)));

    [Fact]
    public void Formato_24_horas_ignora_AM_o_PM()
    {
        Assert.Equal(At(17), Resolve(17, 0, DayPeriod.Am, At(9)));
        Assert.Equal(At(0).AddDays(1), Resolve(0, 0, DayPeriod.Pm, At(9)));
    }

    [Fact]
    public void Doce_en_auto_puede_ser_medianoche_o_mediodia()
    {
        Assert.Equal(At(12), Resolve(12, 0, DayPeriod.Auto, At(9)));
        Assert.Equal(At(0).AddDays(1), Resolve(12, 0, DayPeriod.Auto, At(13)));
    }

    [Fact] public void La_hora_exacta_actual_cuenta_como_ya_pasada() =>
        Assert.Equal(At(17).AddDays(1), Resolve(17, 0, DayPeriod.Auto, At(17)));

    [Fact]
    public void Valores_invalidos_devuelven_nulo()
    {
        Assert.Null(Resolve(24, 0, DayPeriod.Auto, At(9)));
        Assert.Null(Resolve(-1, 0, DayPeriod.Auto, At(9)));
        Assert.Null(Resolve(5, 60, DayPeriod.Auto, At(9)));
    }
}

public class RemainingTimeTests
{
    private const long Min = 60_000L;
    private const long Hour = 60 * Min;
    private const long Day = 24 * Hour;

    [Fact]
    public void Menos_de_un_minuto_cuenta_como_1_min()
    {
        Assert.Equal("1 min", RemainingTime.Format(0));
        Assert.Equal("1 min", RemainingTime.Format(30_000));
        Assert.Equal("1 min", RemainingTime.Format(-5 * Min));
    }

    [Fact]
    public void Solo_minutos()
    {
        Assert.Equal("12 min", RemainingTime.Format(12 * Min));
        Assert.Equal("59 min", RemainingTime.Format(59 * Min + 59_000));
    }

    [Fact]
    public void Horas_y_minutos()
    {
        Assert.Equal("2 h 10 min", RemainingTime.Format(2 * Hour + 10 * Min));
        Assert.Equal("1 h 0 min", RemainingTime.Format(Hour));
        Assert.Equal("23 h 59 min", RemainingTime.Format(Day - Min));
    }

    [Fact]
    public void Dias_y_horas_sin_minutos()
    {
        Assert.Equal("1 d 0 h", RemainingTime.Format(Day));
        Assert.Equal("3 d 4 h", RemainingTime.Format(3 * Day + 4 * Hour + 20 * Min));
        Assert.Equal("7 d 0 h", RemainingTime.Format(7 * Day));
    }
}

public class ExactTimeInputTests
{
    private static readonly DateTimeOffset Now = At(16, 30);

    [Fact]
    public void Cinco_en_automatico_a_las_16_30_es_las_17_de_hoy() =>
        Assert.Equal(At(17), ExactTimeInput.Resolve("5", "00", DayPeriod.Auto, Now, Zone));

    [Fact] public void Cinco_AM_a_las_16_30_es_manana_a_las_5() =>
        Assert.Equal(At(5).AddDays(1), ExactTimeInput.Resolve("5", "00", DayPeriod.Am, Now, Zone));

    [Fact] public void Cinco_PM_es_hoy_a_las_17() =>
        Assert.Equal(At(17), ExactTimeInput.Resolve("5", "00", DayPeriod.Pm, Now, Zone));

    [Fact] public void Una_hora_mayor_a_12_se_toma_en_formato_de_24_horas() =>
        Assert.Equal(At(17, 15), ExactTimeInput.Resolve("17", "15", DayPeriod.Auto, Now, Zone));

    [Fact] public void Campos_vacios_valen_cero_y_la_medianoche_es_manana() =>
        Assert.Equal(At(0).AddDays(1), ExactTimeInput.Resolve("", "", DayPeriod.Auto, Now, Zone));

    [Fact]
    public void Horas_o_minutos_fuera_de_rango_o_con_letras_no_son_validos()
    {
        Assert.Null(ExactTimeInput.Resolve("24", "00", DayPeriod.Auto, Now, Zone));
        Assert.Null(ExactTimeInput.Resolve("5", "60", DayPeriod.Auto, Now, Zone));
        Assert.Null(ExactTimeInput.Resolve("ab", "00", DayPeriod.Auto, Now, Zone));
        Assert.Null(ExactTimeInput.Resolve(" 5", "00", DayPeriod.Auto, Now, Zone));
    }

    [Fact]
    public void La_vista_previa_dice_hoy_o_manana_y_la_hora_con_dos_cifras()
    {
        var today = ExactTimeInput.Resolve("5", "05", DayPeriod.Pm, Now, Zone);
        Assert.Equal("Se reinicia hoy a las 17:05", ExactTimeInput.Describe(today, Now, Zone));
        var tomorrow = ExactTimeInput.Resolve("5", "05", DayPeriod.Am, Now, Zone);
        Assert.Equal("Se reinicia mañana a las 05:05", ExactTimeInput.Describe(tomorrow, Now, Zone));
    }

    [Fact] public void La_vista_previa_de_una_hora_invalida_avisa() =>
        Assert.Equal("Hora no válida", ExactTimeInput.Describe(null, Now, Zone));
}
