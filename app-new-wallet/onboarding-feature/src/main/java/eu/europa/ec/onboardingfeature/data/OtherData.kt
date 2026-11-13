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

import org.json.JSONObject

/**
 * "Otros datos": el celular y el correo que la persona dio al validar su identidad.
 *
 * Van aparte de [PersonalInfo] (que son solo los datos validados de la cédula) y se guardan cifrados en
 * [OtherDataStore]. Son datos de contacto de la persona: ningún emisor los firma, así que la wallet no
 * puede presentarlos a un verifier. Además, el código de verificación del onboarding es una simulación
 * (lo genera y muestra la propia app), por lo que tampoco están verificados.
 */
data class OtherData(
    /** Celular de 10 dígitos. */
    val phone: String?,
    val email: String?,
) {
    fun toJson(): String = JSONObject().apply {
        put("phone", phone)
        put("email", email)
    }.toString()

    companion object {
        fun fromJson(json: String): OtherData? = runCatching {
            val o = JSONObject(json)
            fun s(k: String) = if (o.isNull(k)) null else o.optString(k).ifBlank { null }
            OtherData(phone = s("phone"), email = s("email"))
        }.getOrNull()
    }
}
