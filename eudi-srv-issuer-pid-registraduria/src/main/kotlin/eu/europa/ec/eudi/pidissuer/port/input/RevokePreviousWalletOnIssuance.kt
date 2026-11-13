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
import eu.europa.ec.eudi.pidissuer.domain.IssuedCredential
import eu.europa.ec.eudi.pidissuer.port.out.persistence.GetActiveIssuedCredentialsBySubject
import eu.europa.ec.eudi.pidissuer.port.out.persistence.MarkIssuedCredentialRevoked
import eu.europa.ec.eudi.pidissuer.port.out.persistence.StoreIssuedCredential
import eu.europa.ec.eudi.pidissuer.port.out.status.MarkStatusAsRevoked
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import kotlin.time.Clock

private val log = LoggerFactory.getLogger(RevokePreviousWalletOnIssuance::class.java)

/**
 * Reemisión a una wallet nueva (reinstalación o cambio de celular): cuando a un titular se le emite una credencial en
 * una instalación de wallet distinta de la de sus credenciales vigentes, estas últimas se revocan (ARF: VCR_09, ISSU_62).
 *
 * Es un decorador de [StoreIssuedCredential]: al guardar una credencial nueva con titular, revoca las vigentes del mismo
 * titular cuyo estado de wallet (`clientStatus`) es otro. Las copias de la misma wallet (lotes, otro formato, renovación)
 * no se tocan. Si la revocación falla no se impide la emisión: el registro anterior sigue vigente y la revoca el
 * trabajo periódico cuando la wallet vieja figure revocada.
 *
 * Se activa con `issuer.reissue.revoke-previous-wallet=true` (apagado por defecto).
 */
class RevokePreviousWalletOnIssuance(
    private val delegate: StoreIssuedCredential,
    private val clock: Clock,
    private val getActiveIssuedCredentialsBySubject: GetActiveIssuedCredentialsBySubject,
    private val markStatusAsRevoked: MarkStatusAsRevoked,
    private val markIssuedCredentialRevoked: MarkIssuedCredentialRevoked,
    private val noticeService: RevocationNoticeService? = null,
) : StoreIssuedCredential {
    override suspend fun invoke(credential: IssuedCredential) {
        credential.subject?.let { subject ->
            try {
                revokeOtherWallets(subject, credential)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("The credentials of a previous wallet could not be revoked", e)
            }
        }
        delegate(credential)
    }

    private suspend fun revokeOtherWallets(
        subject: String,
        newCredential: IssuedCredential,
    ) {
        val others = getActiveIssuedCredentialsBySubject(subject, clock.now()).filter { it.clientStatus != newCredential.clientStatus }
        if (others.isEmpty()) return
        val revokedTypes = mutableListOf<String>()
        for (old in others) {
            val done =
                old.status?.let { status ->
                    effect { markStatusAsRevoked(status) }.fold(
                        transform = { true },
                        recover = { false },
                        catch = { false },
                    )
                } ?: true // sin status list no hay nada que poner en 1: solo se anota el registro
            if (done) {
                runCatching { markIssuedCredentialRevoked(old, REASON) }
                    .onFailure { log.warn("Credential revoked but its local record could not be marked", it) }
                revokedTypes.add(old.type)
            }
        }
        log.info("A new wallet was issued credentials: {} credential(s) of the previous wallet revoked", revokedTypes.size)
        if (revokedTypes.isNotEmpty()) noticeService?.notifyRevocation(subject, revokedTypes, REASON)
    }

    companion object {
        const val REASON = "reissued-new-wallet"
    }
}
