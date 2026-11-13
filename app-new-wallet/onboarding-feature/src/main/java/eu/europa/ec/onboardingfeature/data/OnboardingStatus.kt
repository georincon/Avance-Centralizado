package eu.europa.ec.onboardingfeature.data

import android.content.Context
import org.koin.core.annotation.Factory

/** Marca persistente de que el usuario ya completó la validación de identidad (primer arranque). */
@Factory
class OnboardingStatus(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("onboarding_status", Context.MODE_PRIVATE)

    val isCompleted: Boolean get() = prefs.getBoolean(KEY_COMPLETED, false)

    fun markCompleted() {
        prefs.edit().putBoolean(KEY_COMPLETED, true).apply()
    }

    private companion object {
        const val KEY_COMPLETED = "completed"
    }
}
