package app.claudeck.ui.account

import app.claudeck.domain.usecase.AccountFormResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountFormViewModelTest {
    private val form = AccountForm(accountId = "abc", name = "Trabajo", purpose = "Código", email = "", color = 0xFF112233.toInt())

    @Test fun `arranca sin formulario abierto`() = assertNull(AccountFormViewModel().form.value)

    @Test fun `abrir guarda los valores iniciales y cerrar los quita`() {
        val vm = AccountFormViewModel()
        vm.open(form)
        assertEquals(form, vm.form.value)
        vm.dismiss()
        assertNull(vm.form.value)
    }

    @Test fun `una cuenta nueva se abre sin id`() {
        val vm = AccountFormViewModel()
        vm.open(form.copy(accountId = null))
        assertNull(vm.form.value!!.accountId)
    }

    @Test fun `validar usa las reglas del dominio`() {
        val vm = AccountFormViewModel()
        assertEquals(AccountFormResult.Invalid("Poné un nombre"), vm.validate(" ", "", "", form.color))
        assertEquals(AccountFormResult.Valid("Casa", "", "", form.color), vm.validate("Casa", "", "", form.color))
    }
}
