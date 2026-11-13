/*
 * Copyright (c) 2026 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

package eu.europa.ec.onboardingfeature.data

import eu.europa.ec.businesslogic.controller.storage.PrefsController
import org.koin.core.annotation.Factory

/** Guarda [OtherData] cifrado, igual que [PersonalInfoStore] guarda los datos de la cédula. */
@Factory
class OtherDataStore(private val prefs: PrefsController) {

    suspend fun save(data: OtherData) = prefs.setString(KEY, data.toJson())

    suspend fun load(): OtherData? =
        prefs.getString(KEY, "").takeIf { it.isNotBlank() }?.let { OtherData.fromJson(it) }

    private companion object {
        const val KEY = "other_data_v1"
    }
}
