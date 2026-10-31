package app.claudeck.ui.accountmenu

import androidx.lifecycle.ViewModel
import app.claudeck.domain.Clock
import app.claudeck.domain.usecase.AccountMenuModel
import app.claudeck.domain.usecase.BuildAccountMenu
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Menú abierto: de qué cuenta es, su color (íconos y punto) y las opciones ya armadas. */
data class AccountMenuState(val accountId: String, val color: Int, val model: AccountMenuModel)

@HiltViewModel
class AccountMenuViewModel @Inject constructor(private val clock: Clock) : ViewModel() {
    private val _menu = MutableStateFlow<AccountMenuState?>(null)
    val menu: StateFlow<AccountMenuState?> = _menu

    fun open(accountId: String, color: Int, name: String, purpose: String, email: String, limitUntil: Long) {
        _menu.value = AccountMenuState(
            accountId, color,
            BuildAccountMenu(name, purpose, email, limitUntil, clock.now(), clock.zone(), Locale.getDefault())
        )
    }

    fun dismiss() { _menu.value = null }
}
