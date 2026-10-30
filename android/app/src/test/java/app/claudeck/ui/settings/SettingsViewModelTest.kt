package app.claudeck.ui.settings

import app.claudeck.domain.model.AiService
import app.claudeck.domain.model.AppSettings
import app.claudeck.domain.repository.SettingsRepository
import app.claudeck.ui.theme.ServiceColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private class FakeSettings(initial: AppSettings) : SettingsRepository {
        val flow = MutableStateFlow(initial)
        override fun current() = flow.value
        override fun observe(): Flow<AppSettings> = flow
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val claudeDark = AppSettings(AiService.CLAUDE, dark = true, bubbleEnabled = false)

    @Test fun `el estado inicial sale de los ajustes guardados`() {
        val s = SettingsViewModel(FakeSettings(claudeDark)).state.value
        assertEquals(AiService.CLAUDE, s.service)
        assertTrue(s.dark)
        assertFalse(s.bubbleOn)
        assertEquals(ServiceColors.surface(AiService.CLAUDE, true), s.surface)
        assertEquals(ServiceColors.onSurface(AiService.CLAUDE, true), s.onSurface)
    }

    @Test fun `cambiar de servicio o de tema repinta los colores`() {
        val repo = FakeSettings(claudeDark)
        val vm = SettingsViewModel(repo)
        repo.flow.value = AppSettings(AiService.REPLIT, dark = false, bubbleEnabled = false)
        assertEquals(AiService.REPLIT, vm.state.value.service)
        assertEquals(0xFFFBF8F4.toInt(), vm.state.value.surface)
        assertEquals(0xFF1F1B18.toInt(), vm.state.value.onSurface)
    }

    @Test fun `el color de acento lo define la cuenta activa`() {
        val vm = SettingsViewModel(FakeSettings(claudeDark))
        vm.setAccent(0xFF81B29A.toInt())
        assertEquals(0xFF81B29A.toInt(), vm.state.value.accent)
    }

    @Test fun `la burbuja se muestra segun lo que informa la pantalla (permiso incluido)`() {
        val vm = SettingsViewModel(FakeSettings(claudeDark.copy(bubbleEnabled = true)))
        assertTrue(vm.state.value.bubbleOn)       // guardada como activa
        vm.setBubbleShown(false)                  // pero falta el permiso de overlay
        assertFalse(vm.state.value.bubbleOn)
        vm.setBubbleShown(true)
        assertTrue(vm.state.value.bubbleOn)
    }
}
