package app.claudeck

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Punto de entrada de Hilt. El resto de la app (todavía con vistas XML) sigue funcionando igual. */
@HiltAndroidApp
class ClaudeckApp : Application()
