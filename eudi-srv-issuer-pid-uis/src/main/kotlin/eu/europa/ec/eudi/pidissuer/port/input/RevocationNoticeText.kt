/*
 * Copyright (c) 2023-2026 European Commission
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.europa.ec.eudi.pidissuer.port.input

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Instant
import kotlin.time.toJavaInstant

/**
 * Texto del aviso de revocación, en lenguaje claro (CIR 2024/2977 art. 5(3): conciso, accesible y claro).
 */
object RevocationNoticeText {
    /** Motivos que ofrece el panel y la API. Cualquier otro código se informa como decisión de la entidad. */
    val REASON_CODES: Set<String> = setOf("user-request", "data-changed", "wallet-revoked", "fraud", "legal", "other")

    private val zone = ZoneId.of("America/Bogota")
    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

    fun reasonLabel(code: String): String =
        when (code) {
            "user-request" -> "usted solicitó la revocación"
            "data-changed" -> "cambiaron los datos que contenía la credencial"
            "wallet-revoked" -> "su billetera digital fue revocada"
            "fraud" -> "se detectó un posible uso indebido o suplantación de identidad"
            "legal" -> "una decisión legal o administrativa"
            else -> "una decisión de la entidad emisora"
        }

    fun credentialName(type: String): String =
        when (type) {
            "urn:eudi:pid:1", "eu.europa.ec.eudi.pid.1" -> "Identificación personal (PID)"
            "urn:eudi:academic:credential:1" -> "Credencial académica"
            "urn:co:uis:carnet-estudiantil:1" -> "Carnet estudiantil"
            "org.iso.18013.5.1.mDL" -> "Licencia de conducción móvil"
            else -> type
        }

    fun formatDate(at: Instant): String = dateFormat.format(at.toJavaInstant().atZone(zone))

    fun build(
        issuerName: String,
        credentialTypes: List<String>,
        reason: String,
        revokedAt: Instant,
        blocked: Boolean,
    ): String {
        val names = credentialTypes.distinct().joinToString(", ") { credentialName(it) }
        val next =
            if (blocked) {
                "Por ahora no es posible emitirle una nueva credencial. Si cree que es un error, comuníquese con $issuerName."
            } else {
                "Puede solicitar una nueva credencial a $issuerName."
            }
        return "Su credencial ($names) emitida por $issuerName fue revocada el ${formatDate(revokedAt)} por el siguiente " +
            "motivo: ${reasonLabel(reason)}. Una credencial revocada deja de ser válida ante cualquier verificador y no " +
            "se puede reactivar. $next Elimine la credencial revocada de su billetera."
    }
}
