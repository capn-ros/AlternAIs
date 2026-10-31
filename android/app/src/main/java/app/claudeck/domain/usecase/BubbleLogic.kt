package app.claudeck.domain.usecase

/** Posición en píxeles absolutos de la pantalla. */
data class BubblePoint(val x: Int, val y: Int)

/** De qué lado de la pantalla está la cabeza y, por lo tanto, hacia dónde se despliega la lista. */
data class BubbleExpansion(val right: Boolean, val above: Boolean)

/** Geometría de la burbuja flotante (la cabeza redonda y su lista de cuentas). */
object BubbleLayout {
    private fun clamp(v: Int, max: Int) = v.coerceIn(0, maxOf(0, max))

    /** Mantiene la cabeza dentro de la pantalla. */
    fun clampHead(x: Int, y: Int, screenW: Int, screenH: Int, head: Int) =
        BubblePoint(clamp(x, screenW - head), clamp(y, screenH - head))

    /** Posición inicial: la guardada (si hay) o pegada a la derecha, a un tercio de la altura. */
    fun initialHead(savedX: Int, savedY: Int, screenW: Int, screenH: Int, head: Int, edgeMargin: Int): BubblePoint {
        val x = if (savedX >= 0) savedX else screenW - head - edgeMargin
        val y = if (savedY >= 0) savedY else screenH / 3
        return clampHead(x, y, screenW, screenH, head)
    }

    /** Se considera arrastre cuando el dedo se movió más que el umbral del sistema en algún eje. */
    fun isDrag(dx: Float, dy: Float, slop: Int): Boolean = kotlin.math.abs(dx) > slop || kotlin.math.abs(dy) > slop

    /** Al soltar, la cabeza se pega al borde más cercano. */
    fun snapTargetX(headX: Int, screenW: Int, head: Int, edgeMargin: Int): Int =
        if (headX + head / 2 < screenW / 2) edgeMargin else screenW - head - edgeMargin

    /** La lista se abre hacia el lado con más lugar. */
    fun expansion(headX: Int, headY: Int, screenW: Int, screenH: Int, head: Int) =
        BubbleExpansion(right = headX + head / 2 > screenW / 2, above = headY + head / 2 > screenH / 2)

    /** Esquina de la ventana con la lista abierta, de tamaño [w] x [h], sin salirse de la pantalla. */
    fun expandedWindow(
        headX: Int, headY: Int, head: Int, pad: Int, w: Int, h: Int, screenW: Int, screenH: Int, e: BubbleExpansion
    ): BubblePoint = BubblePoint(
        clamp(if (e.right) headX + head + pad - w else headX - pad, screenW - w),
        clamp(if (e.above) headY + head + pad - h else headY - pad, screenH - h)
    )

    /** La fila «Ocultar» va lo más lejos posible de la cabeza para no tocarla sin querer. */
    fun <T> orderRows(accounts: List<T>, hide: T, above: Boolean): List<T> =
        if (above) listOf(hide) + accounts else accounts + hide

    /** Las filas aparecen en cascada empezando por la más cercana a la cabeza. */
    fun <T> animationOrder(rows: List<T>, above: Boolean): List<T> = if (above) rows.reversed() else rows
}

/** Cuenta tal como la muestra la burbuja. */
data class BubbleAccount(val id: String, val name: String, val limitUntil: Long) {
    fun isResting(now: Long) = limitUntil > now
}

/** Insignia de la cabeza y textos de la lista. */
object BubbleStatus {
    const val GREEN = "#3DA35D"
    const val RED = "#D64545"
    const val ORANGE = "#F2A33A"

    fun freeCount(accounts: List<BubbleAccount>, now: Long) = accounts.count { !it.isResting(now) }

    /** Sin cuentas no hay insignia; con cuentas, verde si alguna está libre y roja si ninguna. */
    fun badgeColor(accounts: List<BubbleAccount>, now: Long): String? =
        if (accounts.isEmpty()) null else if (freeCount(accounts, now) > 0) GREEN else RED

    fun label(account: BubbleAccount, now: Long): String =
        if (account.isResting(now)) "${account.name} · ⏳ ${RemainingTime.format(account.limitUntil - now)}"
        else "${account.name} · Disponible"

    /** Color del texto de la fila: naranja si descansa, blanco si no. */
    fun labelColor(account: BubbleAccount, now: Long): String = if (account.isResting(now)) ORANGE else "#FFFFFF"

    /** Cuenta marcada como actual: la elegida o, si no hay, la primera. */
    fun currentId(accounts: List<BubbleAccount>, selectedId: String?): String? = selectedId ?: accounts.firstOrNull()?.id
}
