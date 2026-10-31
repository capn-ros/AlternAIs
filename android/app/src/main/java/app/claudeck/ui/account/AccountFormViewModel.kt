package app.claudeck.ui.account

import androidx.lifecycle.ViewModel
import app.claudeck.domain.usecase.AccountFormResult
import app.claudeck.domain.usecase.ValidateAccountForm
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Valores con los que se abre el formulario. [accountId] null = cuenta nueva. */
data class AccountForm(
    val accountId: String?,
    val name: String,
    val purpose: String,
    val email: String,
    val color: Int
)

@HiltViewModel
class AccountFormViewModel @Inject constructor() : ViewModel() {
    private val _form = MutableStateFlow<AccountForm?>(null)

    /** Formulario abierto (null = ninguno). */
    val form: StateFlow<AccountForm?> = _form

    fun open(form: AccountForm) { _form.value = form }
    fun dismiss() { _form.value = null }

    fun validate(name: String, purpose: String, email: String, color: Int): AccountFormResult =
        ValidateAccountForm(name, purpose, email, color)
}
