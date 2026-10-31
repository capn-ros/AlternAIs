package app.claudeck.domain.usecase

import app.claudeck.domain.model.Account
import app.claudeck.domain.model.AiService
import app.claudeck.domain.policy.EmailPolicy
import app.claudeck.domain.policy.UrlRestorePolicy

/** Una cuenta tal como viene en el archivo de exportación (sin id: los ids se crean al importar). */
data class ImportedAccount(
    val name: String,
    val purpose: String = "",
    val colorHex: String = "",
    val limitUntil: Long = 0L,
    val limitStart: Long = 0L,
    val lastUrl: String = "",
    val email: String = "",
    val selected: Boolean = false
)

data class ImportPlan(val accounts: List<Account>, val added: Int, val skipped: Int, val newSelectedId: String?)

/**
 * Reglas de importación (idénticas en Android y Windows): se omiten nombres repetidos (sin importar
 * mayúsculas) o vacíos, máximo 50 por servicio, solo se conservan límites todavía vigentes y nunca
 * se importan sesiones. Si la mitad no tenía cuenta activa, queda activa la marcada en el archivo.
 */
class PlanImport(private val newId: () -> String, private val palette: List<Int>) {
    operator fun invoke(
        service: AiService,
        existing: List<Account>,
        incoming: List<ImportedAccount>,
        hasSelected: Boolean,
        now: Long
    ): ImportPlan {
        val list = existing.toMutableList()
        var added = 0
        var skipped = 0
        var selectedId: String? = null
        for (item in incoming.take(MAX_PER_SERVICE)) {
            val name = item.name.trim().take(Account.MAX_NAME)
            if (name.isEmpty() || list.any { it.name.equals(name, ignoreCase = true) }) {
                skipped++
                continue
            }
            val active = item.limitUntil > now
            val account = Account(
                id = newId(),
                name = name,
                purpose = item.purpose.trim().take(Account.MAX_PURPOSE),
                color = parseColor(item.colorHex) ?: palette[list.size % palette.size],
                limitUntil = if (active) item.limitUntil else 0L,
                limitStart = if (active) item.limitStart else 0L,
                lastUrl = UrlRestorePolicy.restorable(service, item.lastUrl).orEmpty(),
                email = EmailPolicy.clean(item.email)
            )
            if (!hasSelected && selectedId == null && (item.selected || list.isEmpty())) selectedId = account.id
            list.add(account)
            added++
        }
        return ImportPlan(list, added, skipped, selectedId)
    }

    companion object {
        const val MAX_PER_SERVICE = 50
        private val RGB = Regex("^#[0-9a-fA-F]{6}$")
        private val ARGB = Regex("^#[0-9a-fA-F]{8}$")

        fun parseColor(hex: String): Int? = when {
            RGB.matches(hex) -> (0xFF000000L or hex.substring(1).toLong(16)).toInt()
            ARGB.matches(hex) -> hex.substring(1).toLong(16).toInt()
            else -> null
        }
    }
}
