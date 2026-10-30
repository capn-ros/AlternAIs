package app.claudeck.data

import app.claudeck.domain.model.Account
import app.claudeck.domain.model.AiService
import app.claudeck.domain.policy.UrlRestorePolicy
import org.json.JSONArray
import org.json.JSONObject

/**
 * Formato guardado en disco. Es el mismo que usa la versión anterior (`optString/optLong` con
 * valores por defecto), así actualizar encima de lo ya instalado no pierde cuentas ni sesiones.
 */
object AccountJson {
    fun decode(service: AiService, raw: String?): List<Account> {
        if (raw.isNullOrBlank()) return emptyList()
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
                    lastUrl = UrlRestorePolicy.restorable(service, o.optString("lastUrl", "")).orEmpty(),
                    email = o.optString("email", ""),
                    autoReply = o.optString("autoReply", "")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun encode(service: AiService, accounts: List<Account>): String {
        val arr = JSONArray()
        accounts.forEach { a ->
            arr.put(
                JSONObject()
                    .put("id", a.id)
                    .put("name", a.name)
                    .put("purpose", a.purpose)
                    .put("color", a.color)
                    .put("limitUntil", a.limitUntil)
                    .put("limitStart", a.limitStart)
                    .put("lastUrl", UrlRestorePolicy.restorable(service, a.lastUrl).orEmpty())
                    .put("email", a.email)
                    .put("autoReply", a.autoReply)
            )
        }
        return arr.toString()
    }
}
