package app.claudeck.domain.usecase

import app.claudeck.domain.Clock
import app.claudeck.domain.model.Account

/** Marca una cuenta en descanso hasta [untilMs]. Si ese momento ya pasó, no cambia nada. */
class MarkLimit(private val clock: Clock) {
    operator fun invoke(account: Account, untilMs: Long): Account {
        val now = clock.now()
        return if (untilMs > now) account.copy(limitUntil = untilMs, limitStart = now) else account
    }
}

fun Account.withoutLimit(): Account = copy(limitUntil = 0L, limitStart = 0L)

/** Resultado de limpiar descansos vencidos: la lista nueva y las cuentas que acaban de quedar disponibles. */
data class ExpiredLimits(val accounts: List<Account>, val expired: List<Account>)

class ExpireLimits(private val clock: Clock) {
    operator fun invoke(accounts: List<Account>): ExpiredLimits {
        val now = clock.now()
        val expired = accounts.filter { it.limitUntil > 0L && it.limitUntil <= now }
        if (expired.isEmpty()) return ExpiredLimits(accounts, emptyList())
        val ids = expired.map { it.id }.toSet()
        return ExpiredLimits(accounts.map { if (it.id in ids) it.withoutLimit() else it }, expired)
    }
}

/** Primera cuenta disponible distinta de la actual (para el aviso «Ir a otra cuenta disponible»). */
fun List<Account>.firstAvailableExcept(currentId: String?, now: Long): Account? =
    firstOrNull { it.id != currentId && !it.isResting(now) }
