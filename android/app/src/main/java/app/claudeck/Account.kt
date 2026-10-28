package app.claudeck

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Cada servicio es una "mitad" de la app: sitio propio y cuentas propias (guardadas por separado). */
enum class Service(
    val key: String,
    val label: String,
    val startUrl: String,
    val hosts: List<String>,
    /** Tema del servicio: Claude va oscuro y Replit claro (barra, cajón y página web). */
    val dark: Boolean,
    /** Fondo de la barra y del cajón (en Replit es el crema de su web). */
    val surface: Int,
    /** Color del texto y de los íconos neutros sobre ese fondo. */
    val onSurface: Int
) {
    CLAUDE("claude", "Claude", "https://claude.ai/", listOf("claude.ai"), true, 0xFF1F1B18.toInt(), 0xFFF5EFE6.toInt()),
    REPLIT("replit", "Replit", "https://replit.com/", listOf("replit.com"), false, 0xFFFBF8F4.toInt(), 0xFF1F1B18.toInt());

    companion object {
        fun fromKey(k: String?): Service = values().firstOrNull { it.key == k } ?: CLAUDE
    }
}

/** Solo se restaura una URL HTTPS del sitio del servicio; nunca un destino externo guardado en el estado. */
internal fun urlForRestore(service: Service, raw: String?): String? {
    val value = raw?.takeIf { it.isNotBlank() && it.length <= 4096 } ?: return null
    return try {
        val uri = Uri.parse(value)
        val host = uri.host?.lowercase() ?: return null
        if (
            uri.scheme?.equals("https", ignoreCase = true) != true ||
            service.hosts.none { h -> host == h || host.endsWith(".$h") } ||
            uri.userInfo != null ||
            (uri.port != -1 && uri.port != 443)
        ) null else uri.buildUpon().clearQuery().fragment(null).build().toString()
    } catch (_: Exception) {
        null
    }
}

/** Compatibilidad: restauración para claude.ai. */
internal fun claudeUrlForRestore(raw: String?): String? = urlForRestore(Service.CLAUDE, raw)

data class Account(
    val id: String,
    var name: String,
    var purpose: String,
    var color: Int,
    /** Momento (epoch ms) hasta el que la cuenta está "descansando" por límite de uso. 0 = disponible. */
    var limitUntil: Long = 0L,
    /** Momento (epoch ms) en que se marcó el límite; sirve para calcular qué parte del descanso ya pasó. 0 = no se sabe. */
    var limitStart: Long = 0L,
    /** Última pantalla de Claude visitada; solo se restaura si sigue siendo un URL HTTPS de claude.ai. */
    var lastUrl: String = "",
    /** Correo con el que se inicia sesión (opcional, sin contraseña); sirve para recordarlo y exportarlo. */
    var email: String = "",
    /** Mensaje que se escribe y se envía solo en el chat cuando termina el descanso ("" = ninguno). */
    var autoReply: String = ""
) {
    /** Cada cuenta usa un perfil de WebView propio: cookies y datos totalmente separados. */
    val profileName: String get() = "acc_$id"

    fun limitActive(now: Long = System.currentTimeMillis()): Boolean = limitUntil > now

    /**
     * Qué fracción del descanso falta (1 = recién marcado, 0 = terminó). Si la marca es vieja y no
     * tiene inicio guardado, se supone el plazo estándar más cercano (5 h, 24 h, 7 d o 30 d).
     */
    fun limitFraction(now: Long = System.currentTimeMillis()): Float {
        if (!limitActive(now)) return 0f
        val remaining = limitUntil - now
        val total = if (limitStart in 1 until limitUntil) {
            limitUntil - limitStart
        } else {
            val h = 3_600_000L
            listOf(5 * h, 24 * h, 7 * 24 * h, 30 * 24 * h).firstOrNull { it >= remaining } ?: remaining
        }
        return (remaining.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }

    companion object {
        fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(12)
    }
}

/**
 * Persistencia en SharedPreferences("claudeck"). La comparten la Activity y el servicio de la
 * burbuja (mismo proceso), así que ambos ven siempre los mismos datos.
 */
class AccountStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = context.getSharedPreferences("claudeck", Context.MODE_PRIVATE)

    /** Mitad de la app en uso. Cada servicio tiene sus propias cuentas y su propia cuenta activa. */
    var service: Service
        get() = Service.fromKey(prefs.getString("service", null))
        set(value) {
            prefs.edit().putString("service", value.key).apply()
        }

    /** Tema (oscuro/claro) del servicio en uso. Cada servicio recuerda el suyo; por defecto Claude oscuro y Replit claro. */
    var dark: Boolean
        get() = prefs.getBoolean("dark_${service.key}", service.dark)
        set(value) {
            prefs.edit().putBoolean("dark_${service.key}", value).apply()
        }

    /** Fondo de la barra y del cajón según el tema del servicio en uso. */
    val surface: Int
        get() = when {
            // Colores medidos de las webs: Claude claro #FFFFFF / oscuro #191919; Replit claro crema #FBF8F4 / oscuro #1E1E1F.
            service == Service.CLAUDE -> if (dark) 0xFF191919.toInt() else 0xFFFFFFFF.toInt()
            else -> if (dark) 0xFF1E1E1F.toInt() else 0xFFFBF8F4.toInt()
        }

    /** Texto e íconos neutros sobre ese fondo. */
    val onSurface: Int
        get() = when {
            service == Service.CLAUDE -> if (dark) 0xFFF0F0F0.toInt() else 0xFF1A1A1A.toInt()
            else -> if (dark) 0xFFF0F0F0.toInt() else 0xFF1F1B18.toInt()
        }

    // Claude conserva las claves originales (retrocompatible); los demás llevan sufijo.
    private fun accountsKey(s: Service) = if (s == Service.CLAUDE) "accounts" else "accounts_${s.key}"
    private fun selectedKey(s: Service) = if (s == Service.CLAUDE) "selected" else "selected_${s.key}"

    fun load(): List<Account> = loadFor(service)

    /** Cuentas de un servicio concreto (sirve para exportar las dos mitades). */
    fun loadFor(svc: Service): List<Account> {
        val raw = prefs.getString(accountsKey(svc), null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Account(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    purpose = o.optString("purpose", ""),
                    color = o.getInt("color"),
                    limitUntil = o.optLong("limitUntil", 0L),
                    limitStart = o.optLong("limitStart", 0L),
                    lastUrl = urlForRestore(svc, o.optString("lastUrl", "")) ?: "",
                    email = o.optString("email", ""),
                    autoReply = o.optString("autoReply", "")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(list: List<Account>) = saveFor(service, list)

    fun saveFor(svc: Service, list: List<Account>) {
        val arr = JSONArray()
        list.forEach { a ->
            arr.put(
                JSONObject()
                    .put("id", a.id)
                    .put("name", a.name)
                    .put("purpose", a.purpose)
                    .put("color", a.color)
                    .put("limitUntil", a.limitUntil)
                    .put("limitStart", a.limitStart)
                    .put("lastUrl", urlForRestore(svc, a.lastUrl) ?: "")
                    .put("email", a.email)
                    .put("autoReply", a.autoReply)
            )
        }
        prefs.edit().putString(accountsKey(svc), arr.toString()).apply()
        // Cada vez que cambian las cuentas se reprograman las alarmas de fin de descanso.
        LimitAlarms.reschedule(appContext)
    }

    /** Mensajes guardados para pegar en el chat (compartidos por Claude y Replit). */
    var snippets: List<String>
        get() {
            val raw = prefs.getString("snippets", null) ?: return DEFAULT_SNIPPETS
            return try {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { arr.getString(it) }.filter { it.isNotBlank() }
            } catch (_: Exception) {
                DEFAULT_SNIPPETS
            }
        }
        set(value) {
            val arr = JSONArray()
            value.filter { it.isNotBlank() }.take(50).forEach { arr.put(it.take(4000)) }
            prefs.edit().putString("snippets", arr.toString()).apply()
        }

    fun selectedIdFor(svc: Service): String? = prefs.getString(selectedKey(svc), null)

    fun setSelectedFor(svc: Service, id: String?) {
        prefs.edit().putString(selectedKey(svc), id).apply()
    }

    var selectedId: String?
        get() = prefs.getString(selectedKey(service), null)
        set(value) {
            prefs.edit().putString(selectedKey(service), value).apply()
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

    /** Última página abierta en el navegador interno. */
    var browserLastUrl: String?
        get() = prefs.getString("browser_last_url", null)
        set(value) {
            prefs.edit().putString("browser_last_url", value).apply()
        }

    /** Dónde guarda el navegador lo que descarga: "default" (Descargas), "ask" (preguntar cada vez) o "folder" (carpeta fija). */
    var browserDownloadMode: String
        get() = prefs.getString("browser_dl_mode", "default") ?: "default"
        set(value) {
            prefs.edit().putString("browser_dl_mode", value).apply()
        }

    var browserDownloadDir: String?
        get() = prefs.getString("browser_dl_dir", null)
        set(value) {
            prefs.edit().putString("browser_dl_dir", value).apply()
        }

    /** Ya se le sugirió probar la burbuja (solo una vez). */
    var bubbleTipShown: Boolean
        get() = prefs.getBoolean("bubble_tip_shown", false)
        set(value) {
            prefs.edit().putBoolean("bubble_tip_shown", value).apply()
        }

    /** El tutorial de bienvenida se muestra una sola vez por instalación. */
    var tutorialShown: Boolean
        get() = prefs.getBoolean("tutorial_shown", false)
        set(value) {
            prefs.edit().putBoolean("tutorial_shown", value).apply()
        }
}

/** Mensajes de ejemplo la primera vez; se pueden editar y borrar. */
internal val DEFAULT_SNIPPETS = listOf(
    "Seguí donde te quedaste.",
    "Antes de hacer nada, explicame lo siguiente: ",
    "Resumí lo anterior en pocos puntos.",
    "Explicámelo de forma simple, con un ejemplo."
)
