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
package eu.europa.ec.eudi.pidissuer.port.out.registraduria

import kotlinx.datetime.LocalDate
import kotlin.time.Duration

/**
 * Resultado de una sesión de onboarding, tal como lo reporta el backend de onboarding
 * (lectura del PDF417/OCR de la cédula + comparación facial). Es la evidencia que la
 * Registraduría homologa contra su base antes de ofrecer el PID.
 */
data class OnboardingSession(
    val decision: String?,
    val status: String?,
    val documentNumber: String?,
    val firstNames: String,
    val lastNames: String,
    val birthDate: LocalDate?,
)

sealed interface OnboardingSessionResult {
    data class Found(
        val session: OnboardingSession,
    ) : OnboardingSessionResult

    data object NotFound : OnboardingSessionResult

    data class Unavailable(
        val reason: String,
    ) : OnboardingSessionResult
}

fun interface GetOnboardingSession {
    suspend operator fun invoke(sessionId: String): OnboardingSessionResult
}

/** Ticket de un solo uso que liga una oferta de credenciales a una cédula ya homologada. */
sealed interface TicketRedemption {
    data class Redeemed(
        val documentNumber: String,
    ) : TicketRedemption

    data object NotFound : TicketRedemption

    data object Expired : TicketRedemption
}

interface OfferTickets {
    /** [ttl] opcional: vigencia propia del ticket (por defecto, la del almacén). */
    fun issue(documentNumber: String, ttl: Duration? = null): String

    fun redeem(ticket: String): TicketRedemption
}

/** Resultado de la comprobación de la selfie. */
sealed interface SelfieCheck {
    /** La selfie sirve para comparar (un solo rostro, tamaño y nitidez suficientes). */
    data object Ok : SelfieCheck

    /** La selfie no sirve (sin rostro, borrosa...): [reason] se le muestra a quien la tomó. */
    data class Bad(
        val reason: String,
    ) : SelfieCheck

    data class Unavailable(
        val reason: String,
    ) : SelfieCheck
}

sealed interface FaceMatchResult {
    data class Match(
        val score: Double,
    ) : FaceMatchResult

    data class NoMatch(
        val score: Double?,
    ) : FaceMatchResult

    data class Unavailable(
        val reason: String,
    ) : FaceMatchResult
}

/**
 * Comparación biométrica contra la foto de referencia de la Registraduría. Solo mide calidad de captura y parecido facial:
 * NO es prueba de vida (una foto de la persona mostrada a la cámara podría pasar).
 */
interface FaceMatcher {
    suspend fun checkSelfie(selfie: ByteArray): SelfieCheck

    suspend fun match(reference: ByteArray, selfie: ByteArray): FaceMatchResult
}

/**
 * Compara la selfie que quedó guardada en la sesión de onboarding con la foto de referencia de la Registraduría. La selfie
 * no sale del servicio de onboarding: el issuer solo envía la foto del registro y recibe el veredicto.
 */
fun interface CompareOnboardingSelfie {
    suspend operator fun invoke(sessionId: String, reference: ByteArray): FaceMatchResult
}
