package app.claudeck.data

import android.content.SharedPreferences
import app.claudeck.domain.model.Account
import app.claudeck.domain.model.AiService
import app.claudeck.domain.repository.AccountRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Implementación sobre SharedPreferences("claudeck") con las MISMAS claves de siempre:
 * Claude usa `accounts`/`selected`; los demás servicios llevan sufijo (`accounts_replit`, ...).
 * [onSaved] reemplaza al viejo acoplamiento con las alarmas (se reprograman al guardar).
 */
class SharedPrefsAccountRepository(
    private val prefs: SharedPreferences,
    private val onSaved: () -> Unit = {}
) : AccountRepository {

    override fun observeAccounts(service: AiService): Flow<List<Account>> = callbackFlow {
        val key = accountsKey(service)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
            if (changed == key) trySend(read(service))
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(read(service))
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    override suspend fun accounts(service: AiService): List<Account> = read(service)

    override suspend fun save(service: AiService, accounts: List<Account>) {
        prefs.edit().putString(accountsKey(service), AccountJson.encode(service, accounts)).apply()
        onSaved()
    }

    override suspend fun selectedId(service: AiService): String? = prefs.getString(selectedKey(service), null)

    override suspend fun setSelectedId(service: AiService, id: String?) {
        prefs.edit().putString(selectedKey(service), id).apply()
    }

    private fun read(service: AiService) = AccountJson.decode(service, prefs.getString(accountsKey(service), null))

    private fun accountsKey(s: AiService) = if (s == AiService.CLAUDE) "accounts" else "accounts_${s.key}"
    private fun selectedKey(s: AiService) = if (s == AiService.CLAUDE) "selected" else "selected_${s.key}"
}
