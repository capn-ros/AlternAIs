package app.claudeck.domain.model

/** Reglas de los mensajes guardados (listas inmutables: cada operación devuelve una lista nueva). */
object Snippets {
    const val MAX_COUNT = 50
    const val MAX_LENGTH = 4000

    /** Mensajes de ejemplo la primera vez; se pueden editar y borrar. */
    val DEFAULTS = listOf(
        "Seguí donde te quedaste.",
        "Antes de hacer nada, explicame lo siguiente: ",
        "Resumí lo anterior en pocos puntos.",
        "Explicámelo de forma simple, con un ejemplo."
    )

    /** Saca los vacíos, deja a lo sumo 50 y corta cada uno a 4000 caracteres. */
    fun normalize(list: List<String>): List<String> =
        list.filter { it.isNotBlank() }.take(MAX_COUNT).map { it.take(MAX_LENGTH) }

    fun add(list: List<String>, text: String): List<String> =
        if (text.isBlank()) list else normalize(list + text)

    fun replace(list: List<String>, index: Int, text: String): List<String> =
        if (text.isBlank() || index !in list.indices) list else normalize(list.toMutableList().also { it[index] = text })

    /** Sube un mensaje una posición (el primero no se mueve). */
    fun moveUp(list: List<String>, index: Int): List<String> {
        if (index !in 1 until list.size) return list
        return list.toMutableList().also { it.add(index - 1, it.removeAt(index)) }
    }

    fun remove(list: List<String>, index: Int): List<String> =
        if (index !in list.indices) list else list.toMutableList().also { it.removeAt(index) }
}
