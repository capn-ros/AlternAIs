package app.claudeck

import android.content.Context
import android.net.Uri
import app.claudeck.data.AccountJson
import app.claudeck.domain.model.AiService
import app.claudeck.domain.policy.UrlRestorePolicy
import org.json.JSONArray

/** Cada servicio es una "mitad" de la app: sitio propio y cuentas propias (guardadas por separado). */
enum class Service(
    val key: String,
    val label: String,
    val startUrl: String,
    val hosts: List<String>,
    /** Tema del servicio: Claude va oscuro por defecto (barra, cajón y página web). */
    val dark: Boolean,
    /** Fondo de la barra y del cajón . */
    val surface: Int,
    /** Color del texto y de los íconos neutros sobre ese fondo. */
    val onSurface: Int
) {
    CLAUDE("claude", "Claude", "https://claude.ai/", listOf("claude.ai"), true, 0xFF1F1B18.toInt(), 0xFFF5EFE6.toInt());

    companion object {
        fun fromKey(k: String?): Service = values().firstOrNull { it.key == k } ?: CLAUDE
    }
}

/** Solo se restaura una URL HTTPS del sitio del servicio; nunca un destino externo guardado en el estado. */
internal fun urlForRestore(service: Service, raw: String?): String? =
    UrlRestorePolicy.restorable(AiService.fromKey(service.key), raw)

/** Compatibilidad: restauración para claude.ai. */
internal fun claudeUrlForRestore(raw: String?): String? = urlForRestore(Service.CLAUDE, raw)

/** La pantalla usa la misma cuenta inmutable del dominio; los cambios se hacen con `copy`. */
typealias Account = app.claudeck.domain.model.Account

/** ¿La cuenta está en descanso ahora? (atajo con la hora actual por defecto) */
fun Account.limitActive(now: Long = System.currentTimeMillis()): Boolean = isResting(now)

/** Fracción del descanso que falta, con la hora actual por defecto. */
fun Account.limitFraction(): Float = limitFraction(System.currentTimeMillis())

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

    /** Tema (oscuro/claro) del servicio en uso. Cada servicio recuerda el suyo; por defecto Claude oscuro. */
    var dark: Boolean
        get() = prefs.getBoolean("dark_${service.key}", service.dark)
        set(value) {
            prefs.edit().putBoolean("dark_${service.key}", value).apply()
        }

    /** Fondo de la barra y del cajón según el tema del servicio en uso. */
    val surface: Int
        get() = when {
            // Colores medidos de las webs: Claude claro #FFFFFF / oscuro #191919.
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
    fun loadFor(svc: Service): List<Account> =
        AccountJson.decode(AiService.fromKey(svc.key), prefs.getString(accountsKey(svc), null))

    fun save(list: List<Account>) = saveFor(service, list)

    fun saveFor(svc: Service, list: List<Account>) {
        val raw = AccountJson.encode(AiService.fromKey(svc.key), list)
        prefs.edit().putString(accountsKey(svc), raw).apply()
        // Cada vez que cambian las cuentas se reprograman las alarmas de fin de descanso.
        LimitAlarms.reschedule(appContext)
    }

    /** Mensajes guardados para pegar en el chat . */
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
