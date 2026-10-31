package app.claudeck.domain.usecase

import kotlin.math.abs

/** Qué hace la barra de cuentas ante un gesto. */
sealed interface DockGesture {
    /** Pasar a la cuenta siguiente (+1) o anterior (-1). */
    data class Step(val delta: Int) : DockGesture

    /** Plegar (true) o desplegar (false) la barra. */
    data class SetCollapsed(val collapsed: Boolean) : DockGesture

    object None : DockGesture
}

/** Umbrales y reglas de los gestos sobre la barra de cuentas (velocidades en px/s, distancias en px). */
object DockGestures {
    const val FLING_HORIZONTAL_MIN = 600f
    const val FLING_VERTICAL_MIN = 500f

    /**
     * Un envión sobre la fila de información. Horizontal rápido: hacia la izquierda pasa a la cuenta
     * siguiente y hacia la derecha a la anterior. Vertical rápido: hacia abajo pliega la barra y hacia
     * arriba la despliega (como el cajón de un launcher). Cualquier otro caso no hace nada.
     */
    fun fling(vx: Float, vy: Float): DockGesture = when {
        abs(vx) > abs(vy) && abs(vx) > FLING_HORIZONTAL_MIN -> DockGesture.Step(if (vx < 0) 1 else -1)
        abs(vy) > abs(vx) && abs(vy) > FLING_VERTICAL_MIN -> DockGesture.SetCollapsed(vy > 0)
        else -> DockGesture.None
    }

    /** Un arrastre empieza cuando es vertical (más del doble de la tolerancia y 1,5 veces más que el movimiento horizontal). */
    fun startsVerticalDrag(dx: Float, dy: Float, touchSlop: Float): Boolean =
        abs(dy) > touchSlop * 2f && abs(dy) > abs(dx) * 1.5f

    /** Al soltar un arrastre: true = plegar (fue hacia abajo), false = desplegar, null = muy corto, no hace nada. */
    fun dragCollapse(dy: Float, minDistance: Float): Boolean? = if (abs(dy) > minDistance) dy > 0 else null
}
