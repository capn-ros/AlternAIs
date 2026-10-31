package app.claudeck.domain.usecase

/** Cuenta con descanso, tal como la necesita el planificador de alarmas. */
data class AlarmCandidate(val id: String, val name: String, val limitUntil: Long)

/** Una alarma a programar: en [at] (epoch ms) avisa que [name] ya puede usarse. */
data class AlarmEntry(val id: String, val name: String, val at: Long)

/** Qué alarmas hay que programar: solo las cuentas cuyo descanso todavía no terminó. */
object AlarmPlan {
    fun schedule(accounts: List<AlarmCandidate>, now: Long): List<AlarmEntry> =
        accounts.filter { it.limitUntil > now }.map { AlarmEntry(it.id, it.name, it.limitUntil) }
}

/** Qué hacer cuando suena la alarma de fin de descanso. */
enum class AlarmAction {
    /** La cuenta no tiene mensaje automático: solo avisa. */
    NOTIFY_ONLY,

    /** Hay mensaje automático y la app está abierta: lo envía ella. */
    SEND_IN_APP,

    /** Hay mensaje automático y la app está cerrada: lo envía un servicio en segundo plano. */
    SEND_IN_BACKGROUND
}

object AlarmDispatch {
    fun decide(hasAutoReply: Boolean, appOpen: Boolean): AlarmAction = when {
        !hasAutoReply -> AlarmAction.NOTIFY_ONLY
        appOpen -> AlarmAction.SEND_IN_APP
        else -> AlarmAction.SEND_IN_BACKGROUND
    }

    /** ¿El envío arrancó? Solo puede fallar el servicio en segundo plano. */
    fun sendStarted(action: AlarmAction, serviceStarted: Boolean): Boolean =
        action != AlarmAction.SEND_IN_BACKGROUND || serviceStarted
}

/** Respuesta desde la notificación: texto escrito o mensaje guardado, recortado; null si quedó vacío. */
object ReplyText {
    fun resolve(typed: String?, preset: String?): String? = (typed ?: preset).orEmpty().trim().ifEmpty { null }

    /** Con la app abierta usa su chat; cerrada, la responde el servicio. */
    fun sendInApp(appOpen: Boolean): Boolean = appOpen
}

/** Un mensaje guardado ofrecido como botón de la notificación. [slot] es su posición original (0 o 1). */
data class SnippetAction(val slot: Int, val label: String, val text: String)

/** Textos de las notificaciones de fin de descanso y de resultado del envío. */
object LimitNotificationText {
    fun doneTitle(name: String) = "«$name» ya puede usarse"

    fun doneText(auto: Boolean, started: Boolean): String = when {
        auto && started -> "Terminó el descanso. Enviando el mensaje automático…"
        auto -> "Terminó el descanso. Abrí la app para que se envíe el mensaje automático."
        else -> "Terminó el descanso por límite de uso. Podés responder desde acá."
    }

    /** Con mensaje automático no se ofrece responder a mano. */
    fun offersReply(auto: Boolean) = !auto

    /** Los dos primeros mensajes guardados (los vacíos se saltan pero conservan su lugar). */
    fun snippetActions(snippets: List<String>): List<SnippetAction> =
        snippets.take(2).mapIndexedNotNull { k, s ->
            if (s.isBlank()) null else SnippetAction(k, if (s.length > 22) s.take(22) + "…" else s, s)
        }

    fun resultTitle(name: String, ok: Boolean) =
        if (ok) "Mensaje enviado a «$name»" else "No se pudo enviar el mensaje a «$name»"

    fun resultText(text: String, ok: Boolean): String {
        val short = if (text.length > 80) text.take(80) + "…" else text
        return if (ok) "«$short»" else "Copié el mensaje: abrí la cuenta y pegalo. «$short»"
    }

    fun resultBigText(text: String, ok: Boolean): String =
        if (ok) "«$text»" else "Copié el mensaje al portapapeles: abrí la cuenta y pegalo.\n«$text»"
}

/** Qué hacer después de cada intento de escribir el mensaje en la página. */
sealed class SendStep {
    object Sent : SendStep()
    data class RetryIn(val delayMs: Long) : SendStep()
    object GiveUp : SendStep()
}

/** Tiempos del envío en segundo plano. */
object AutoSendPolicy {
    const val FIRST_ATTEMPT_DELAY_MS = 3_000L
    const val RETRY_DELAY_MS = 2_000L
    const val MAX_RETRIES = 22
    const val SETTLE_MS = 3_500L
    const val TOTAL_TIMEOUT_MS = 80_000L

    /** [attempt] es el número de intento (desde 0); [scriptOk] si el script encontró la caja de texto. */
    fun next(attempt: Int, scriptOk: Boolean): SendStep = when {
        scriptOk -> SendStep.Sent
        attempt < MAX_RETRIES -> SendStep.RetryIn(RETRY_DELAY_MS)
        else -> SendStep.GiveUp
    }
}
