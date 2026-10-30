package app.claudeck.domain.usecase

/** Reglas para moverse entre cuentas. Trabajan con la lista de ids en el orden en que se muestran. */
object AccountNavigation {
    /**
     * Cuenta siguiente (+1) o anterior (-1), dando la vuelta en los extremos. Null si hay menos de dos
     * cuentas. Si la actual no está en la lista, se parte desde la primera.
     */
    fun step(ids: List<String>, currentId: String?, delta: Int): String? {
        if (ids.size < 2) return null
        val idx = ids.indexOf(currentId).coerceAtLeast(0)
        return ids[Math.floorMod(idx + delta, ids.size)]
    }

    /**
     * Sentido de la animación al cambiar de cuenta: 1 si la nueva está más a la derecha, -1 si más a la
     * izquierda, 0 si no hay cuenta anterior (primera selección) o no cambia.
     */
    fun direction(ids: List<String>, fromId: String?, toId: String): Int {
        val from = ids.indexOf(fromId)
        if (from < 0) return 0
        return Integer.signum(ids.indexOf(toId) - from)
    }
}

/** Cuándo la barra de cuentas se pliega sola. */
object DockPolicy {
    /** No se pliega durante el tutorial, si ya está plegada, con un cajón grande abierto ni sin cuentas. */
    fun shouldAutoHide(tutorialActive: Boolean, collapsed: Boolean, subPanelOpen: Boolean, accountCount: Int): Boolean =
        !tutorialActive && !collapsed && !subPanelOpen && accountCount > 0
}
