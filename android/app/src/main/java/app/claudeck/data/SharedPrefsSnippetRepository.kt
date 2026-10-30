package app.claudeck.data

import android.content.SharedPreferences
import app.claudeck.domain.model.Snippets
import app.claudeck.domain.repository.SnippetRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import org.json.JSONArray

/** Formato en disco: la clave `snippets` con un arreglo JSON de textos (igual que la versión anterior). */
object SnippetsJson {
    /** Sin dato guardado o con dato roto: los mensajes de ejemplo. Un arreglo vacío guardado sigue siendo vacío. */
    fun decode(raw: String?): List<String> {
        if (raw == null) return Snippets.DEFAULTS
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }.filter { it.isNotBlank() }
        } catch (_: Exception) {
            Snippets.DEFAULTS
        }
    }

    fun encode(list: List<String>): String {
        val arr = JSONArray()
        Snippets.normalize(list).forEach { arr.put(it) }
        return arr.toString()
    }
}

class SharedPrefsSnippetRepository(private val prefs: SharedPreferences) : SnippetRepository {
    override fun current(): List<String> = SnippetsJson.decode(prefs.getString(KEY, null))

    override fun observe(): Flow<List<String>> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == KEY) trySend(current()) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(current())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    override fun save(list: List<String>) {
        prefs.edit().putString(KEY, SnippetsJson.encode(list)).apply()
    }

    private companion object { const val KEY = "snippets" }
}
