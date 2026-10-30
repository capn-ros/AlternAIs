package app.claudeck.data

import android.content.SharedPreferences
import app.claudeck.domain.model.AiService
import app.claudeck.domain.model.AppSettings
import app.claudeck.domain.repository.SettingsRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Mismas claves de siempre: `service`, `dark_<servicio>` (Claude oscuro y Replit claro por defecto) y `bubble_enabled`. */
class SharedPrefsSettingsRepository(private val prefs: SharedPreferences) : SettingsRepository {

    override fun current(): AppSettings {
        val service = AiService.fromKey(prefs.getString("service", null))
        return AppSettings(
            service = service,
            dark = prefs.getBoolean("dark_${service.key}", service == AiService.CLAUDE),
            bubbleEnabled = prefs.getBoolean("bubble_enabled", false)
        )
    }

    override fun observe(): Flow<AppSettings> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "service" || key == "bubble_enabled" || key?.startsWith("dark_") == true) trySend(current())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(current())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()
}
