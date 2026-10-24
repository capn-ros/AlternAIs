package app.claudeck

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Account(
    val id: String,
    var name: String,
    var purpose: String,
    var color: Int,
    /** Momento (epoch ms) hasta el que la cuenta está "descansando" por límite de uso. 0 = disponible. */
    var limitUntil: Long = 0L
) {
    /** Cada cuenta usa un perfil de WebView propio: cookies y datos totalmente separados. */
    val profileName: String get() = "acc_$id"

    fun limitActive(now: Long = System.currentTimeMillis()): Boolean = limitUntil > now

    companion object {
        fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(12)
    }
}

/**
 * Persistencia en SharedPreferences("claudeck"). La comparten la Activity y el servicio de la
 * burbuja (mismo proceso), así que ambos ven siempre los mismos datos.
 */
class AccountStore(context: Context) {
    private val prefs = context.getSharedPreferences("claudeck", Context.MODE_PRIVATE)

    fun load(): List<Account> {
        val raw = prefs.getString("accounts", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Account(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    purpose = o.optString("purpose", ""),
                    color = o.getInt("color"),
                    limitUntil = o.optLong("limitUntil", 0L)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(list: List<Account>) {
        val arr = JSONArray()
        list.forEach { a ->
            arr.put(
                JSONObject()
                    .put("id", a.id)
                    .put("name", a.name)
                    .put("purpose", a.purpose)
                    .put("color", a.color)
                    .put("limitUntil", a.limitUntil)
            )
        }
        prefs.edit().putString("accounts", arr.toString()).apply()
    }

    var selectedId: String?
        get() = prefs.getString("selected", null)
        set(value) {
            prefs.edit().putString("selected", value).apply()
        }

    /** Burbuja flotante activada por el usuario. */
    var bubbleEnabled: Boolean
        get() = prefs.getBoolean("bubble_enabled", false)
        set(value) {
            prefs.edit().putBoolean("bubble_enabled", value).apply()
        }

    /** Posición (esquina superior izquierda, px) de la burbuja; -1 = todavía sin definir. */
    var bubbleX: Int
        get() = prefs.getInt("bubble_x", -1)
        set(value) {
            prefs.edit().putInt("bubble_x", value).apply()
        }

    var bubbleY: Int
        get() = prefs.getInt("bubble_y", -1)
        set(value) {
            prefs.edit().putInt("bubble_y", value).apply()
        }

    /** Panel de cuentas (chips) plegado para ganar pantalla. */
    var dockCollapsed: Boolean
        get() = prefs.getBoolean("dock_collapsed", false)
        set(value) {
            prefs.edit().putBoolean("dock_collapsed", value).apply()
        }

    /** Ya se le sugirió probar la burbuja (solo una vez). */
    var bubbleTipShown: Boolean
        get() = prefs.getBoolean("bubble_tip_shown", false)
        set(value) {
            prefs.edit().putBoolean("bubble_tip_shown", value).apply()
        }
}
