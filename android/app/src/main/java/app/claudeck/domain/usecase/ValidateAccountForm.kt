package app.claudeck.domain.usecase

import app.claudeck.domain.model.Account
import app.claudeck.domain.policy.EmailPolicy

sealed interface AccountFormResult {
    /** Datos ya limpios, listos para guardar. */
    data class Valid(val name: String, val purpose: String, val email: String, val color: Int) : AccountFormResult

    /** El nombre es lo único obligatorio. */
    data class Invalid(val nameError: String) : AccountFormResult
}

/**
 * Reglas del formulario de cuenta: nombre obligatorio (máx. 24), propósito opcional (máx. 60) y
 * correo opcional (solo si tiene forma de correo; nunca contraseñas).
 */
object ValidateAccountForm {
    operator fun invoke(name: String, purpose: String, email: String, color: Int): AccountFormResult {
        val cleanName = name.trim().take(Account.MAX_NAME).trim()
        if (cleanName.isEmpty()) return AccountFormResult.Invalid("Poné un nombre")
        return AccountFormResult.Valid(
            name = cleanName,
            purpose = purpose.trim().take(Account.MAX_PURPOSE),
            email = EmailPolicy.clean(email),
            color = color
        )
    }
}
