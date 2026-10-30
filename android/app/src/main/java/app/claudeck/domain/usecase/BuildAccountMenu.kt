package app.claudeck.domain.usecase

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Lo que se puede hacer desde el menú de una cuenta. */
enum class AccountMenuAction { EDIT, RELOAD, MARK_LIMIT, CLEAR_LIMIT, SIGN_OUT, DELETE }

data class AccountMenuItem(val action: AccountMenuAction, val label: String)

/** Menú ya armado: título, línea de estado y opciones en el orden en que se muestran. */
data class AccountMenuModel(val title: String, val subtitle: String, val items: List<AccountMenuItem>)

/** Pregunta de confirmación para las acciones que borran datos. */
data class Confirmation(val title: String, val message: String)

object BuildAccountMenu {
    private const val SEPARATOR = " · "

    /** [limitUntil] 0 = disponible. «Quitar marca de límite» solo aparece si la cuenta está en descanso. */
    operator fun invoke(
        name: String, purpose: String, email: String, limitUntil: Long,
        now: Long, zone: ZoneId, locale: Locale
    ): AccountMenuModel {
        val resting = limitUntil > now
        val status = if (resting) {
            "En descanso hasta " + DateTimeFormatter.ofPattern("EEE HH:mm", locale)
                .format(Instant.ofEpochMilli(limitUntil).atZone(zone))
        } else "Disponible"
        val items = buildList {
            add(AccountMenuItem(AccountMenuAction.EDIT, "Editar nombre, propósito o color"))
            add(AccountMenuItem(AccountMenuAction.RELOAD, "Recargar"))
            add(AccountMenuItem(AccountMenuAction.MARK_LIMIT, "Marcar límite alcanzado…"))
            if (resting) add(AccountMenuItem(AccountMenuAction.CLEAR_LIMIT, "Quitar marca de límite"))
            add(AccountMenuItem(AccountMenuAction.SIGN_OUT, "Cerrar sesión y borrar datos"))
            add(AccountMenuItem(AccountMenuAction.DELETE, "Eliminar cuenta"))
        }
        return AccountMenuModel(name, listOf(purpose, email, status).filter { it.isNotBlank() }.joinToString(SEPARATOR), items)
    }

    /** Null si la acción no pide confirmación. */
    fun confirmation(action: AccountMenuAction, accountName: String, serviceLabel: String): Confirmation? = when (action) {
        AccountMenuAction.SIGN_OUT -> Confirmation(
            "Cerrar sesión",
            "Se cierra la sesión de «$accountName» y se borran sus cookies y datos en este dispositivo."
        )
        AccountMenuAction.DELETE -> Confirmation(
            "Eliminar cuenta",
            "Se quita «$accountName» de la app y se borran sus datos en este dispositivo. Tu cuenta de $serviceLabel no se toca."
        )
        else -> null
    }
}
