package app.claudeck

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import androidx.core.app.RemoteInput
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat

/** "faltan 2 h 10 min", "3 d 4 h", "12 min" (mínimo 1 min). */
internal fun formatRemaining(ms: Long): String {
    val totalMin = (ms / 60_000L).coerceAtLeast(1L)
    val days = totalMin / (60 * 24)
    val hours = (totalMin % (60 * 24)) / 60
    val mins = totalMin % 60
    return when {
        days > 0 -> "$days d $hours h"
        hours > 0 -> "$hours h $mins min"
        else -> "$mins min"
    }
}

/**
 * Alarma por cada cuenta en descanso: cuando termina el plazo suena un aviso (aunque la app esté cerrada).
 * Se reprograma cada vez que se guardan las cuentas (AccountStore.saveFor) y al reiniciar el teléfono.
 */
object LimitAlarms {
    private const val ACTION = "app.claudeck.action.LIMIT_DONE"
    private const val CHANNEL = "limits"
    private const val PREF_IDS = "alarm_ids"

    private fun pending(ctx: Context, id: String, name: String?, flags: Int): PendingIntent? {
        val i = Intent(ctx, LimitAlarmReceiver::class.java)
            .setAction(ACTION)
            .setData(Uri.parse("alternais://limit/$id"))
        if (name != null) i.putExtra("id", id).putExtra("name", name)
        return PendingIntent.getBroadcast(ctx, id.hashCode(), i, flags or PendingIntent.FLAG_IMMUTABLE)
    }

    fun reschedule(ctx: Context) {
        try {
            val app = ctx.applicationContext
            val prefs = app.getSharedPreferences("claudeck", Context.MODE_PRIVATE)
            val am = app.getSystemService(AlarmManager::class.java) ?: return
            // Cancela las anteriores.
            prefs.getStringSet(PREF_IDS, emptySet())?.forEach { id ->
                pending(app, id, null, PendingIntent.FLAG_NO_CREATE)?.let {
                    am.cancel(it)
                    it.cancel()
                }
            }
            val store = AccountStore(app)
            val now = System.currentTimeMillis()
            val ids = HashSet<String>()
            for (svc in Service.values()) {
                for (a in store.loadFor(svc)) {
                    if (a.limitUntil <= now) continue
                    val pi = pending(app, a.id, a.name, PendingIntent.FLAG_UPDATE_CURRENT) ?: continue
                    val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
                    try {
                        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, a.limitUntil, pi)
                        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, a.limitUntil, pi)
                    } catch (_: SecurityException) {
                        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, a.limitUntil, pi)
                    }
                    ids.add(a.id)
                }
            }
            prefs.edit().putStringSet(PREF_IDS, ids).apply()
        } catch (_: Exception) {
        }
    }

    private fun channel(ctx: Context): NotificationManager? {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return null
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Fin del descanso de una cuenta", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        return nm
    }

    private fun openIntent(ctx: Context, id: String, reply: Boolean, code: Int): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_SELECT, id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (reply) i.putExtra(MainActivity.EXTRA_REPLY, id)
        return PendingIntent.getActivity(ctx, code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun post(ctx: Context, id: String, nm: NotificationManager, n: Notification) {
        try {
            nm.notify(("limit" + id).hashCode(), n)
        } catch (_: SecurityException) {
            // Sin permiso de notificaciones.
        }
    }

    internal const val ACTION_REPLY = "app.claudeck.action.REPLY"
    internal const val KEY_REPLY = "reply_text"

    /** Acción de la notificación que envía un texto sin abrir la app (cuadro para escribir o mensaje guardado). */
    private fun replyIntent(ctx: Context, id: String, text: String?, code: Int): PendingIntent {
        val i = Intent(ctx, ReplyReceiver::class.java)
            .setAction(ACTION_REPLY)
            .setData(Uri.parse("alternais://reply/$id/$code"))
            .putExtra("id", id)
        if (text != null) i.putExtra("text", text)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (text == null && Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE
            else if (text == null) 0 else PendingIntent.FLAG_IMMUTABLE)
        return PendingIntent.getBroadcast(ctx, code, i, flags)
    }

    /**
     * Aviso de fin de descanso. Con mensaje automático dice qué pasa; sin él se puede responder desde la
     * misma notificación: cuadro para escribir y los primeros mensajes guardados, sin abrir la app.
     */
    internal fun notifyDone(ctx: Context, id: String, name: String, auto: Boolean = false, started: Boolean = true) {
        val nm = channel(ctx) ?: return
        val text = when {
            auto && started -> "Terminó el descanso. Enviando el mensaje automático…"
            auto -> "Terminó el descanso. Abrí la app para que se envíe el mensaje automático."
            else -> "Terminó el descanso por límite de uso. Podés responder desde acá."
        }
        val b = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_bubble)
            .setContentTitle("«$name» ya puede usarse")
            .setContentText(text)
            .setContentIntent(openIntent(ctx, id, false, id.hashCode()))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_ALL)
        if (!auto) addReplyActions(ctx, b, id)
        post(ctx, id, nm, b.build())
    }

    private fun addReplyActions(ctx: Context, b: NotificationCompat.Builder, id: String) {
        val ri = RemoteInput.Builder(KEY_REPLY).setLabel("Escribí la respuesta").build()
        b.addAction(
            NotificationCompat.Action.Builder(R.drawable.ic_message, "Responder", replyIntent(ctx, id, null, id.hashCode() + 1))
                .addRemoteInput(ri)
                .setAllowGeneratedReplies(false)
                .build()
        )
        val snips = try { AccountStore(ctx).snippets } catch (_: Exception) { emptyList() }
        snips.take(2).forEachIndexed { k, s ->
            if (s.isBlank()) return@forEachIndexed
            val label = if (s.length > 22) s.take(22) + "…" else s
            b.addAction(R.drawable.ic_message, label, replyIntent(ctx, id, s, id.hashCode() + 2 + k))
        }
    }

    /** Resultado del mensaje automático: se envió o falló (reemplaza al aviso de fin de descanso). */
    internal fun notifyResult(ctx: Context, id: String, name: String, ok: Boolean, text: String) {
        val nm = channel(ctx) ?: return
        val short = if (text.length > 80) text.take(80) + "…" else text
        val b = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_bubble)
            .setContentTitle(if (ok) "Mensaje enviado a «$name»" else "No se pudo enviar el mensaje a «$name»")
            .setContentText(if (ok) "«$short»" else "Copié el mensaje: abrí la cuenta y pegalo. «$short»")
            .setStyle(NotificationCompat.BigTextStyle().bigText(if (ok) "«$text»" else "Copié el mensaje al portapapeles: abrí la cuenta y pegalo.\n«$text»"))
            .setContentIntent(openIntent(ctx, id, false, id.hashCode()))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_ALL)
        if (!ok) addReplyActions(ctx, b, id)
        post(ctx, id, nm, b.build())
    }
}

class LimitAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val name = intent.getStringExtra("name") ?: "Cuenta"
        val auto = Service.values().any { svc -> AccountStore(context).loadFor(svc).any { it.id == id && it.autoReply.isNotEmpty() } }
        // Con la app abierta se ocupa ella; si está cerrada, un servicio manda el mensaje en segundo plano.
        var started = true
        if (auto && !MainActivity.alive) started = AutoReplyService.start(context, id, null)
        LimitAlarms.notifyDone(context, id, name, auto, started || MainActivity.alive)
        BubbleService.refresh()
    }
}

/** Reprograma las alarmas al reiniciar el teléfono o actualizar la app (se pierden en ambos casos). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        LimitAlarms.reschedule(context)
    }
}

/** Respuesta desde la notificación (cuadro para escribir o mensaje guardado): se envía sin abrir la app. */
class ReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val typed = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(LimitAlarms.KEY_REPLY)?.toString()
        val text = (typed ?: intent.getStringExtra("text")).orEmpty().trim()
        if (text.isEmpty()) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm?.cancel(("limit" + id).hashCode())
        if (MainActivity.alive) {
            // App abierta: usa su propio chat.
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_SELECT, id)
                    .putExtra(MainActivity.EXTRA_SEND_TEXT, text)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        } else {
            AutoReplyService.start(context, id, text)
        }
    }
}
