package app.claudeck.ui.limits

import androidx.lifecycle.ViewModel
import app.claudeck.domain.Clock
import app.claudeck.domain.model.AiService
import app.claudeck.domain.usecase.DayPeriod
import app.claudeck.domain.usecase.ExactTimeInput
import app.claudeck.domain.usecase.LimitOption
import app.claudeck.domain.usecase.LimitOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Qué diálogo de «marcar límite» hay en pantalla. */
sealed interface LimitDialog {
    object None : LimitDialog
    data class Options(val accountId: String, val service: AiService) : LimitDialog
    data class Exact(val accountId: String) : LimitDialog
}

@HiltViewModel
class LimitViewModel @Inject constructor(private val clock: Clock) : ViewModel() {
    private val _dialog = MutableStateFlow<LimitDialog>(LimitDialog.None)
    val dialog: StateFlow<LimitDialog> = _dialog

    fun openOptions(accountId: String, service: AiService) { _dialog.value = LimitDialog.Options(accountId, service) }
    fun openExact(accountId: String) { _dialog.value = LimitDialog.Exact(accountId) }
    fun dismiss() { _dialog.value = LimitDialog.None }

    fun options(service: AiService): List<LimitOption> = LimitOptions.forService(service)

    /** Momento de reinicio para «dentro de N horas». */
    fun untilFor(option: LimitOption.After): Long = option.untilMs(clock.now())

    /** Hora exacta escrita a mano (null si no es válida). */
    fun resolveExact(hour: String, minute: String, period: DayPeriod): ZonedDateTime? =
        ExactTimeInput.resolve(hour, minute, period, now())

    fun previewExact(hour: String, minute: String, period: DayPeriod): String {
        val now = now()
        return ExactTimeInput.describe(ExactTimeInput.resolve(hour, minute, period, now), now)
    }

    private fun now(): ZonedDateTime = Instant.ofEpochMilli(clock.now()).atZone(clock.zone())
}
