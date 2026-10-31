package app.claudeck.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.claudeck.domain.model.AiService
import app.claudeck.domain.repository.SettingsRepository
import app.claudeck.ui.theme.ServiceColors
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Todo lo que necesita dibujar la pantalla de Ajustes. */
data class SettingsUiState(
    val service: AiService,
    val dark: Boolean,
    val bubbleOn: Boolean,
    /** Color de la cuenta activa (ARGB): íconos, título y pestaña activa. */
    val accent: Int,
    val surface: Int,
    val onSurface: Int
)

@HiltViewModel
class SettingsViewModel @Inject constructor(private val repo: SettingsRepository) : ViewModel() {

    private val accent = MutableStateFlow(DEFAULT_ACCENT)
    // La burbuja solo se muestra «activada» si además está el permiso de dibujar sobre otras apps.
    private val bubbleShown = MutableStateFlow(repo.current().bubbleEnabled)

    val state: StateFlow<SettingsUiState> =
        combine(repo.observe(), accent, bubbleShown) { s, a, b -> build(s.service, s.dark, b, a) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, repo.current().let { build(it.service, it.dark, bubbleShown.value, accent.value) })

    fun setAccent(color: Int) { accent.value = color }
    fun setBubbleShown(on: Boolean) { bubbleShown.value = on }

    private fun build(service: AiService, dark: Boolean, bubble: Boolean, accent: Int) = SettingsUiState(
        service, dark, bubble, accent, ServiceColors.surface(service, dark), ServiceColors.onSurface(service, dark)
    )

    private companion object {
        const val DEFAULT_ACCENT = 0xFFD2694A.toInt()
    }
}
