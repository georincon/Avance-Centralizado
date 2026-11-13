/*
 * Copyright (c) 2023-2026 European Commission
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
package eu.europa.ec.eudi.pidissuer.adapter.input.web

import arrow.core.raise.effect
import arrow.core.raise.fold
import eu.europa.ec.eudi.pidissuer.adapter.out.transito.EstadoLicencia
import eu.europa.ec.eudi.pidissuer.adapter.out.transito.LicenciaDocumentNumber
import eu.europa.ec.eudi.pidissuer.adapter.out.transito.LicenciaHomologation
import eu.europa.ec.eudi.pidissuer.adapter.out.attestation.mdl.mdlConfigurationId
import eu.europa.ec.eudi.pidissuer.adapter.out.util.maskDocumentNumber
import eu.europa.ec.eudi.pidissuer.domain.CredentialConfigurationId
import eu.europa.ec.eudi.pidissuer.port.input.CreateCredentialsOffer
import eu.europa.ec.eudi.pidissuer.port.out.persistence.IsSubjectBlocked
import eu.europa.ec.eudi.pidissuer.port.out.transito.CompareOnboardingSelfie
import eu.europa.ec.eudi.pidissuer.port.out.transito.FaceMatchResult
import eu.europa.ec.eudi.pidissuer.port.out.transito.FindLicenciaByDocumentNumber
import eu.europa.ec.eudi.pidissuer.port.out.transito.GetOnboardingSession
import eu.europa.ec.eudi.pidissuer.port.out.transito.OfferTickets
import eu.europa.ec.eudi.pidissuer.port.out.transito.OnboardingSessionResult
import eu.europa.ec.eudi.pidissuer.port.out.transito.TicketRedemption
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.*
import java.security.MessageDigest
import java.util.UUID

/**
 * Puente onboarding -> emisión de la licencia de conducción móvil (mDL) del Tránsito.
 *
 *  - `POST /onboarding/offers` (público): la wallet envía el `sessionId` de su onboarding aprobado. El issuer
 *    verifica server-to-server la sesión, homologa contra la tabla `licencias` y devuelve una oferta
 *    (authorization_code + `issuer_state` = ticket de un solo uso) con el mDL (`mso_mdoc`).
 *  - `POST /internal/v1/tickets/redeem` (interno, `X-Api-Key`): lo llama Keycloak para identificar al
 *    ciudadano a partir del `issuer_state`. No se expone fuera de la red docker (ver haproxy.conf).
 */
class OnboardingOffersApi(
    private val createCredentialsOffer: CreateCredentialsOffer,
    private val getOnboardingSession: GetOnboardingSession,
    private val findLicenciaByDocumentNumber: FindLicenciaByDocumentNumber,
    private val tickets: OfferTickets,
    private val ticketApiKey: String?,
    private val rateLimit: IpRateLimit = IpRateLimit.disabled(),
    private val isSubjectBlocked: IsSubjectBlocked = IsSubjectBlocked { false },
    private val compareSelfie: CompareOnboardingSelfie,
) {
    val router: RouterFunction<ServerResponse> =
        coRouter {
            POST(
                OFFERS,
                contentType(MediaType.APPLICATION_JSON) and accept(MediaType.APPLICATION_JSON, MediaType.ALL),
                ::handleOffer,
            )
            POST(
                REDEEM,
                contentType(MediaType.APPLICATION_JSON),
                ::handleRedeem,
            )
        }

    private suspend fun handleOffer(request: ServerRequest): ServerResponse {
        // Endpoint público: cada intento cuenta, y un cliente bloqueado no llega a consultar nada.
        rateLimit.blocked(request)?.let { return tooManyRequests(it) }
        rateLimit.record(request)

        val body = runCatching { request.awaitBody<OfferRequestTO>() }.getOrNull()
        val sessionId = body?.sessionId?.trim()
        if (sessionId.isNullOrBlank() || runCatching { UUID.fromString(sessionId) }.isFailure) {
            return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", listOf("sessionId must be a UUID"))
        }

        val session =
            when (val result = getOnboardingSession(sessionId)) {
                is OnboardingSessionResult.Found -> result.session
                OnboardingSessionResult.NotFound -> return error(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND")
                is OnboardingSessionResult.Unavailable -> return error(HttpStatus.BAD_GATEWAY, "ONBOARDING_UNAVAILABLE")
            }

        if (session.decision != "APPROVED") {
            return error(HttpStatus.FORBIDDEN, "NOT_APPROVED", listOf("decision=${session.decision ?: "none"}"))
        }
        val documentNumber =
            session.documentNumber ?: return error(HttpStatus.FORBIDDEN, "NOT_APPROVED", listOf("DOCUMENT_NUMBER_MISSING"))

        val licencia =
            findLicenciaByDocumentNumber(LicenciaDocumentNumber(documentNumber))
                ?: return error(HttpStatus.NOT_FOUND, "CEDULA_NOT_FOUND")

        // Titular con la emisión bloqueada (por ejemplo, tras una revocación por fraude): no se le crea oferta
        if (isSubjectBlocked(licencia.documentNumber.value)) {
            log.info("Oferta rechazada: emisión bloqueada para la cédula {}", documentNumber.maskDocumentNumber())
            return error(HttpStatus.FORBIDDEN, "SUBJECT_BLOCKED")
        }

        val homologation = LicenciaHomologation.check(session, licencia)
        if (!homologation.ok) {
            log.info("Homologación fallida para la cédula {}: {}", documentNumber.maskDocumentNumber(), homologation.reasons)
            return error(HttpStatus.FORBIDDEN, "DATA_MISMATCH", homologation.reasons)
        }
        if (licencia.estado != EstadoLicencia.ACTIVA) {
            return error(HttpStatus.FORBIDDEN, "LICENCIA_NOT_ACTIVE", listOf("estado=${licencia.estado}"))
        }
        if (licencia.fotoReferencia == null) {
            return error(HttpStatus.CONFLICT, "CEDULA_INCOMPLETE", listOf("REFERENCE_PHOTO_MISSING"))
        }
        if (licencia.categoriasVigentes(java.time.LocalDate.now()).isEmpty()) {
            return error(HttpStatus.CONFLICT, "LICENCIA_EXPIRED", listOf("NO_VALID_CATEGORIES"))
        }

        // La selfie del onboarding se compara con la foto de REFERENCIA de la licencia (no con la de la tarjeta que fotografió el
        // usuario, que podría ser falsa). Si no se puede comprobar, no se emite.
        when (val face = compareSelfie(sessionId, requireNotNull(licencia.fotoReferencia).bytes)) {
            is FaceMatchResult.Match -> log.info("Selfie del onboarding coincide con la foto de la licencia ({}, score {})", documentNumber.maskDocumentNumber(), face.score)
            is FaceMatchResult.NoMatch -> {
                log.info("Selfie del onboarding NO coincide con la foto de la licencia ({}, score {})", documentNumber.maskDocumentNumber(), face.score)
                return error(HttpStatus.FORBIDDEN, "FACE_MISMATCH")
            }
            is FaceMatchResult.Unavailable -> return error(HttpStatus.BAD_GATEWAY, "FACE_CHECK_UNAVAILABLE", listOf(face.reason))
        }

        val requestedFormats = body.credentialFormats.toSet()
        val mdlIds = licencia.categoriasVigentes(java.time.LocalDate.now()).map { mdlConfigurationId(it.categoria) }
        val ids = requestedFormats.flatMapTo(linkedSetOf()) { format ->
            when (format) {
                FORMAT_MSO_MDOC -> mdlIds
                else -> emptyList()
            }
        }
        if (requestedFormats.isEmpty() || requestedFormats.any { it != FORMAT_MSO_MDOC }) {
            return error(HttpStatus.BAD_REQUEST, "INVALID_CREDENTIAL_FORMATS", listOf("Allowed: $FORMAT_MSO_MDOC"))
        }
        val ticket = tickets.issue(licencia.documentNumber.value)
        return effect {
            createCredentialsOffer(CreateCredentialsOffer.Request(ids, issuerState = ticket))
        }.fold(
            transform = { offerUri ->
                log.info("Oferta mDL creada para la cédula {}", documentNumber.maskDocumentNumber())
                ServerResponse
                    .ok()
                    .json()
                    .bodyValueAndAwait(
                        OfferResponseTO(
                            offerUri = offerUri.toString(),
                            credentialConfigurationIds = ids.map { it.value },
                            expiresInSeconds = TICKET_TTL_SECONDS,
                        ),
                    )
            },
            recover = { err ->
                log.error("No se pudo crear la oferta: {}", err)
                error(HttpStatus.INTERNAL_SERVER_ERROR, "OFFER_ERROR", listOf(err::class.java.simpleName))
            },
        )
    }

    private suspend fun handleRedeem(request: ServerRequest): ServerResponse {
        val expected = ticketApiKey
        if (expected.isNullOrBlank()) return error(HttpStatus.SERVICE_UNAVAILABLE, "TICKETS_DISABLED")
        val provided = request.headers().firstHeader(API_KEY_HEADER).orEmpty()
        val keyOk =
            MessageDigest.isEqual(
                provided.toByteArray(Charsets.UTF_8),
                expected.toByteArray(Charsets.UTF_8),
            )
        if (!keyOk) return error(HttpStatus.UNAUTHORIZED, "INVALID_API_KEY")

        val ticket = runCatching { request.awaitBody<RedeemRequestTO>() }.getOrNull()?.ticket?.trim()
        if (ticket.isNullOrBlank()) return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST")

        return when (val redemption = tickets.redeem(ticket)) {
            TicketRedemption.NotFound -> error(HttpStatus.NOT_FOUND, "TICKET_NOT_FOUND")
            TicketRedemption.Expired -> error(HttpStatus.GONE, "TICKET_EXPIRED")
            is TicketRedemption.Redeemed -> {
                val licencia =
                    findLicenciaByDocumentNumber(LicenciaDocumentNumber(redemption.documentNumber))
                        ?: return error(HttpStatus.NOT_FOUND, "CEDULA_NOT_FOUND")
                ServerResponse
                    .ok()
                    .json()
                    .bodyValueAndAwait(
                        RedeemResponseTO(
                            documentNumber = licencia.documentNumber.value,
                            givenName = licencia.nombres,
                            familyName = licencia.apellidos,
                            birthDate = licencia.fechaNacimiento.toString(),
                            nationality = licencia.nacionalidad,
                        ),
                    )
            }
        }
    }

    private suspend fun tooManyRequests(blocked: AttemptLimiter.Status.Blocked): ServerResponse =
        ServerResponse
            .status(HttpStatus.TOO_MANY_REQUESTS)
            .header("Retry-After", blocked.retryAfterSeconds.toString())
            .json()
            .bodyValueAndAwait(ErrorTO("TOO_MANY_REQUESTS"))

    private suspend fun error(
        status: HttpStatus,
        code: String,
        reasons: List<String> = emptyList(),
    ): ServerResponse =
        ServerResponse
            .status(status)
            .json()
            .bodyValueAndAwait(ErrorTO(code, reasons))

    companion object {
        const val OFFERS: String = "/onboarding/offers"
        const val REDEEM: String = "/internal/v1/tickets/redeem"
        const val API_KEY_HEADER: String = "X-Api-Key"
        const val TICKET_TTL_SECONDS: Long = 600
        const val FORMAT_MSO_MDOC: String = "mso_mdoc"
        /** Oferta predeterminada para flujos web existentes: solo el mDL. */
        private val log = LoggerFactory.getLogger(OnboardingOffersApi::class.java)

    }
}

@Serializable
private data class OfferRequestTO(
    @SerialName("sessionId") val sessionId: String? = null,
    @SerialName("credentialFormats") val credentialFormats: List<String> = emptyList(),
)

@Serializable
private data class OfferResponseTO(
    @SerialName("offerUri") val offerUri: String,
    @SerialName("credentialConfigurationIds") val credentialConfigurationIds: List<String>,
    @SerialName("expiresInSeconds") val expiresInSeconds: Long,
)

@Serializable
private data class RedeemRequestTO(
    @SerialName("ticket") val ticket: String? = null,
)

@Serializable
private data class RedeemResponseTO(
    @SerialName("documentNumber") val documentNumber: String,
    @SerialName("givenName") val givenName: String,
    @SerialName("familyName") val familyName: String,
    @SerialName("birthDate") val birthDate: String,
    @SerialName("nationality") val nationality: String,
)

@Serializable
private data class ErrorTO(
    @SerialName("code") val code: String,
    @SerialName("reasons") val reasons: List<String> = emptyList(),
)
