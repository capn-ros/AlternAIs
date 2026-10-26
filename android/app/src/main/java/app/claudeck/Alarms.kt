package app.claudeck

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
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

    internal fun notifyDone(ctx: Context, id: String, name: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Fin del descanso de una cuenta", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val open = PendingIntent.getActivity(
            ctx, id.hashCode(),
            Intent(ctx, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_SELECT, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n: Notification = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_bubble)
            .setContentTitle("«$name» ya puede usarse")
            .setContentText("Terminó el descanso por límite de uso. Tocá para abrirla.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_ALL)
            .build()
        try {
            nm.notify(("limit" + id).hashCode(), n)
        } catch (_: SecurityException) {
            // Sin permiso de notificaciones.
        }
    }
}

class LimitAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val name = intent.getStringExtra("name") ?: "Cuenta"
        LimitAlarms.notifyDone(context, id, name)
        BubbleService.refresh()
    }
}

/** Reprograma las alarmas al reiniciar el teléfono o actualizar la app (se pierden en ambos casos). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        LimitAlarms.reschedule(context)
    }
}
