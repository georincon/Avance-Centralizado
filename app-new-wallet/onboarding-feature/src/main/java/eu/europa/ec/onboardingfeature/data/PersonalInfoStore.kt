package eu.europa.ec.onboardingfeature.data

import eu.europa.ec.businesslogic.controller.storage.PrefsController
import org.koin.core.annotation.Factory

/** Persistencia cifrada (DataStore + Tink + Android Keystore) de los datos validados de la cédula. */
@Factory
class PersonalInfoStore(private val prefs: PrefsController) {

    suspend fun save(info: PersonalInfo) = prefs.setString(KEY, info.toJson())

    suspend fun load(): PersonalInfo? =
        prefs.getString(KEY, "").takeIf { it.isNotBlank() }?.let { PersonalInfo.fromJson(it) }

    private companion object {
        const val KEY = "personal_info_v1"
    }
}
