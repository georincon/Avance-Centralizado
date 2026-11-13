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
package eu.europa.ec.eudi.pidissuer.adapter.out.notification

import eu.europa.ec.eudi.pidissuer.port.out.notification.DeliverRevocationNotice
import eu.europa.ec.eudi.pidissuer.port.out.notification.DeliveryResult
import eu.europa.ec.eudi.pidissuer.port.out.notification.SubjectContact
import eu.europa.ec.eudi.pidissuer.port.out.persistence.RevocationNotice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient

/**
 * Entrega el aviso a un servicio propio de la entidad (correo, SMS, mensajería...) con un POST JSON al
 * `issuer.revocation.notification.webhook-url`. El emisor no envía correos por sí mismo: el servicio que reciba el
 * POST decide el canal. Sin URL configurada, o sin datos de contacto del titular, el aviso queda pendiente.
 */
class WebhookDeliverRevocationNotice(
    private val webClient: WebClient,
    private val url: String?,
    private val apiKey: String?,
) : DeliverRevocationNotice {
    override suspend fun invoke(
        notice: RevocationNotice,
        contact: SubjectContact?,
    ): DeliveryResult {
        if (url.isNullOrBlank()) return DeliveryResult.NoChannel("no hay un canal de aviso configurado")
        if (contact == null || contact.isEmpty) return DeliveryResult.NoChannel("el titular no tiene datos de contacto registrados")
        return try {
            webClient
                .post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .headers { headers -> apiKey?.takeIf { it.isNotBlank() }?.let { headers.set("X-Api-Key", it) } }
                .bodyValue(
                    WebhookPayload(
                        noticeId = notice.id,
                        subject = notice.subject,
                        message = notice.message,
                        reason = notice.reason,
                        deadlineAt = notice.deadlineAt.toString(),
                        email = contact.email,
                        phone = contact.phone,
                    ),
                ).retrieve()
                .toBodilessEntity()
                .awaitSingle()
            DeliveryResult.Sent("webhook")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DeliveryResult.Failed(e.message?.take(300) ?: e::class.java.simpleName)
        }
    }
}

@Serializable
private data class WebhookPayload(
    @SerialName("noticeId") val noticeId: Long?,
    @SerialName("subject") val subject: String,
    @SerialName("message") val message: String,
    @SerialName("reason") val reason: String,
    @SerialName("deadlineAt") val deadlineAt: String,
    @SerialName("email") val email: String?,
    @SerialName("phone") val phone: String?,
)
