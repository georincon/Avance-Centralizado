package eu.europa.ec.resourceslogic.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Fondo de la app que elige el titular en Configuración → Apariencia. */
enum class AppearanceMode(val key: String) {
    /** Azul marino: el fondo original de la wallet. */
    BLUE("blue"),
    WHITE("white"),
    BLACK("black");

    companion object {
        fun fromKey(key: String?): AppearanceMode = entries.firstOrNull { it.key == key } ?: BLUE
    }
}

/**
 * Apariencia vigente. Es un estado de Compose: al cambiarla toda la app se vuelve a pintar en el momento. Se guarda en
 * las preferencias del teléfono y se recupera al abrir la wallet.
 */
object ThemeAppearance {
    private const val PREFS = "apariencia"
    private const val KEY = "modo"

    var mode: AppearanceMode by mutableStateOf(AppearanceMode.BLUE)
        private set

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        runCatching {
            mode = AppearanceMode.fromKey(
                context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            )
        }
    }

    fun set(context: Context, value: AppearanceMode) {
        mode = value
        loaded = true
        runCatching {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, value.key).apply()
        }
    }
}
