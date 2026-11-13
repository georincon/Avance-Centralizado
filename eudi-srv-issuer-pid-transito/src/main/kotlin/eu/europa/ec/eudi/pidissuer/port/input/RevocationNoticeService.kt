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

import eu.europa.ec.eudi.pidissuer.port.out.notification.DeliverRevocationNotice
import eu.europa.ec.eudi.pidissuer.port.out.notification.DeliveryResult
import eu.europa.ec.eudi.pidissuer.port.out.notification.GetSubjectContact
import eu.europa.ec.eudi.pidissuer.port.out.persistence.IsSubjectBlocked
import eu.europa.ec.eudi.pidissuer.port.out.persistence.NoticeStatus
import eu.europa.ec.eudi.pidissuer.port.out.persistence.RevocationNotice
import eu.europa.ec.eudi.pidissuer.port.out.persistence.StoreRevocationNotice
import eu.europa.ec.eudi.pidissuer.port.out.persistence.UpdateRevocationNotice
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

private val log = LoggerFactory.getLogger(RevocationNoticeService::class.java)

/**
 * Registra el aviso de revocación al titular y trata de entregarlo por el canal configurado.
 *
 * Siempre queda un registro con el plazo de 24 horas: si no hay canal automático o el titular no tiene datos de
 * contacto, el aviso queda PENDIENTE y el administrador debe hacerlo por su cuenta y marcarlo como hecho.
 *
 * Nunca lanza excepciones: un fallo al avisar no puede deshacer ni impedir la revocación, que ya se hizo.
 */
class RevocationNoticeService(
    private val clock: Clock,
    private val store: StoreRevocationNotice,
    private val update: UpdateRevocationNotice,
    private val deliver: DeliverRevocationNotice,
    private val getContact: GetSubjectContact,
    private val isSubjectBlocked: IsSubjectBlocked,
    private val issuerName: String,
) {
    suspend fun notifyRevocation(
        subject: String,
        credentialTypes: List<String>,
        reason: String,
    ) {
        try {
            val now = clock.now()
            val notice =
                RevocationNotice(
                    subject = subject,
                    credentialTypes = credentialTypes.distinct(),
                    reason = reason,
                    message =
                        RevocationNoticeText.build(
                            issuerName,
                            credentialTypes,
                            reason,
                            now,
                            blocked = isSubjectBlocked(subject),
                        ),
                    revokedAt = now,
                    deadlineAt = now + 24.hours,
                )
            val id = store(notice)
            val result = deliver(notice.copy(id = id), getContact(subject))
            when (result) {
                is DeliveryResult.Sent -> update(id, NoticeStatus.SENT, result.channel, null)
                is DeliveryResult.NoChannel -> update(id, NoticeStatus.PENDING, null, result.reason)
                is DeliveryResult.Failed -> update(id, NoticeStatus.FAILED, null, result.reason)
            }
            log.info("Revocation notice {} registered: {}", id, result::class.simpleName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("The revocation notice could not be registered", e)
        }
    }
}
