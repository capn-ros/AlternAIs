package app.claudeck.domain.repository

import app.claudeck.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow

/** Solo lectura por ahora: la lógica vieja de MainActivity sigue escribiendo; la UI nueva se actualiza sola. */
interface SettingsRepository {
    fun current(): AppSettings
    fun observe(): Flow<AppSettings>
}
