package app.claudeck.domain

import java.time.ZoneId

/** Reloj inyectable: los casos de uso no llaman a System.currentTimeMillis(), así se prueban sin esperar. */
interface Clock {
    fun now(): Long
    fun zone(): ZoneId
}

object SystemClock : Clock {
    override fun now(): Long = System.currentTimeMillis()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}
