package app.claudeck.domain.repository

import app.claudeck.domain.model.Account
import app.claudeck.domain.model.AiService
import kotlinx.coroutines.flow.Flow

/** Contrato de persistencia. El dominio no sabe si hay SharedPreferences, DataStore o Room detrás. */
interface AccountRepository {
    fun observeAccounts(service: AiService): Flow<List<Account>>
    suspend fun accounts(service: AiService): List<Account>
    suspend fun save(service: AiService, accounts: List<Account>)
    suspend fun selectedId(service: AiService): String?
    suspend fun setSelectedId(service: AiService, id: String?)
}
