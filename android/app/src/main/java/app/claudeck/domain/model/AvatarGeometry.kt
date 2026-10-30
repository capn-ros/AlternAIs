package app.claudeck.domain.model

import kotlin.math.pow

/**
 * Medidas del círculo de cuenta (disco de color con anillo de tiempo y rayita de «activa»), calculadas
 * a partir del tamaño del lienzo: [width] es el diámetro y [height] suma el aire de arriba y abajo
 * (la rayita de la cuenta activa va en ese aire). Son las mismas proporciones de siempre.
 */
data class AvatarGeometry(val width: Float, val height: Float) {
    val extra: Float get() = (height - width) / 2f
    val cx: Float get() = width / 2f
    val cy: Float get() = height / 2f
    val stroke: Float get() = width * 0.07f
    val ringRadius: Float get() = width / 2f - stroke / 2f
    val diskRadius: Float get() = ringRadius - stroke / 2f - width * 0.05f
    val textSize: Float get() = diskRadius * 0.95f

    /** Rayita de la cuenta activa: mitad del ancho, alto y centro vertical. */
    val markHalfWidth: Float get() = width * 0.17f
    val markHeight: Float get() = extra * 0.42f
    val markCenterY: Float get() = height - extra / 2f

    /** Grados del arco del anillo: lo que falta de descanso (mínimo 2 % para que siempre se vea algo). */
    fun sweepDegrees(fraction: Float): Float = 360f * fraction.coerceAtLeast(MIN_FRACTION)

    private companion object {
        const val MIN_FRACTION = 0.02f
    }
}

/** Colores del texto sobre el disco de una cuenta. */
object AvatarColors {
    private const val DARK_TEXT = 0xFF1F1F1F.toInt()
    private const val LIGHT_TEXT = 0xFFFFFFFF.toInt()

    /** Luminancia relativa (0 negro a 1 blanco), misma fórmula que usa Android. */
    fun luminance(argb: Int): Double {
        fun channel(v: Int): Double {
            val c = v / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel((argb shr 16) and 0xFF) + 0.7152 * channel((argb shr 8) and 0xFF) +
            0.0722 * channel(argb and 0xFF)
    }

    /** Texto oscuro sobre colores claros y blanco sobre los demás. */
    fun textOn(argb: Int): Int = if (luminance(argb) > 0.55) DARK_TEXT else LIGHT_TEXT
}
