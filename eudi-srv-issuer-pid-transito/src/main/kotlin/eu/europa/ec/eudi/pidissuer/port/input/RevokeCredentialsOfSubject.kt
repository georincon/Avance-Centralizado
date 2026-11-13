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

import arrow.core.raise.effect
import arrow.core.raise.fold
import eu.europa.ec.eudi.pidissuer.port.out.persistence.GetActiveIssuedCredentialsBySubject
import eu.europa.ec.eudi.pidissuer.port.out.persistence.MarkIssuedCredentialRevoked
import eu.europa.ec.eudi.pidissuer.port.out.status.MarkStatusAsRevoked
import org.slf4j.LoggerFactory
import kotlin.time.Clock

private val log = LoggerFactory.getLogger(RevokeCredentialsOfSubject::class.java)

/**
 * Revoca todas las credenciales vigentes de un titular: marca cada una como revocada en su status list y anota la
 * revocación (fecha y motivo) en el registro del emisor.
 *
 * No borra nada de la base del emisor: los datos del titular no se tocan y el registro de la credencial se
 * conserva, marcado como revocado.
 *
 * Si el status list falla para una credencial, no se anota y sigue vigente, para poder reintentar. Las credenciales
 * emitidas sin status list no se pueden revocar: se cuentan aparte y se dejan intactas.
 */
class RevokeCredentialsOfSubject(
    private val clock: Clock,
    private val getActiveIssuedCredentialsBySubject: GetActiveIssuedCredentialsBySubject,
    private val markStatusAsRevoked: MarkStatusAsRevoked,
    private val markIssuedCredentialRevoked: MarkIssuedCredentialRevoked,
    private val noticeService: RevocationNoticeService? = null,
) {
    data class Result(
        /** Credenciales marcadas como revocadas en su status list. */
        val revoked: Int,
        /** Credenciales cuyo status list no respondió: siguen vigentes y se pueden reintentar. */
        val failed: Int,
        /** Credenciales emitidas sin status list: no se pueden revocar. */
        val withoutStatus: Int,
    )

    suspend operator fun invoke(
        subject: String,
        reason: String = DEFAULT_REASON,
    ): Result {
        val credentials = getActiveIssuedCredentialsBySubject(subject, clock.now())
        var revoked = 0
        var failed = 0
        var withoutStatus = 0
        val revokedTypes = mutableListOf<String>()
        for (credential in credentials) {
            val status = credential.status
            if (status == null) {
                withoutStatus++
                continue
            }
            val done =
                effect { markStatusAsRevoked(status) }.fold(
                    transform = { true },
                    recover = { error ->
                        log.warn("Failed to revoke credential in status list '{}': {}", status.statusList, error.value)
                        false
                    },
                    catch = { throwable ->
                        log.warn("Failed to revoke credential in status list '{}'", status.statusList, throwable)
                        false
                    },
                )
            if (done) {
                // Ya revocada en su status list. Si el registro local no se puede anotar, la credencial sigue
                // revocada (el estado público manda) y el trabajo periódico la volverá a anotar.
                runCatching { markIssuedCredentialRevoked(credential, reason) }
                    .onFailure { log.warn("Credential revoked but its local record could not be marked", it) }
                revoked++
                revokedTypes.add(credential.type)
            } else {
                failed++
            }
        }
        log.info(
            "Revocation requested for a subject ({}): {} revoked, {} failed, {} without status list",
            reason,
            revoked,
            failed,
            withoutStatus,
        )
        // El titular debe ser informado de la revocación y de su motivo (CIR 2024/2977 art. 5(3))
        if (revokedTypes.isNotEmpty()) noticeService?.notifyRevocation(subject, revokedTypes, reason)
        return Result(revoked, failed, withoutStatus)
    }

    companion object {
        const val DEFAULT_REASON: String = "provider-request"
    }
}
