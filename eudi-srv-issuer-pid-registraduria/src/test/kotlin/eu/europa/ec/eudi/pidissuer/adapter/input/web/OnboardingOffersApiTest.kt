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

import eu.europa.ec.eudi.pidissuer.PidIssuerApplicationTest
import eu.europa.ec.eudi.pidissuer.adapter.out.registraduria.Cedula
import eu.europa.ec.eudi.pidissuer.adapter.out.registraduria.CedulaDocumentNumber
import eu.europa.ec.eudi.pidissuer.adapter.out.registraduria.FotoReferencia
import eu.europa.ec.eudi.pidissuer.adapter.out.registraduria.InMemoryOfferTickets
import eu.europa.ec.eudi.pidissuer.adapter.out.registraduria.Sexo
import eu.europa.ec.eudi.pidissuer.port.input.CreateCredentialsOffer
import eu.europa.ec.eudi.pidissuer.port.out.registraduria.FindCedulaByDocumentNumber
import eu.europa.ec.eudi.pidissuer.port.out.registraduria.GetOnboardingSession
import eu.europa.ec.eudi.pidissuer.port.out.registraduria.OnboardingSession
import eu.europa.ec.eudi.pidissuer.port.out.registraduria.OnboardingSessionResult
import kotlinx.datetime.LocalDate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

@PidIssuerApplicationTest
@TestPropertySource(properties = ["issuer.credentialOffer.allowedSchemes=https,openid-credential-offer,eu-eaa-offer"])
class OnboardingOffersApiTest {
    @Autowired
    private lateinit var createCredentialsOffer: CreateCredentialsOffer

    private val sessionId = UUID.randomUUID().toString()
    private val cedula =
        Cedula(
            documentNumber = CedulaDocumentNumber("12345678"),
            apellidos = "MUNOZ NAVAS",
            nombres = "JOSE GREGORIO",
            fechaNacimiento = LocalDate(1973, 9, 8),
            lugarNacimientoCiudad = "BUCARAMANGA",
            sexo = Sexo.M,
            fotoReferencia = FotoReferencia(byteArrayOf(1, 2, 3), "image/jpeg"),
        )

    private fun approved(birth: LocalDate = LocalDate(1973, 9, 8)) =
        OnboardingSession("APPROVED", "COMPLETED", "12345678", "JOSE GREGORIO", "MUNOZ NAVAS", birth)

    private fun client(
        session: OnboardingSessionResult,
        found: Cedula? = cedula,
        tickets: InMemoryOfferTickets = InMemoryOfferTickets(Clock.System),
        apiKey: String? = "secret",
        face: eu.europa.ec.eudi.pidissuer.port.out.registraduria.FaceMatchResult = eu.europa.ec.eudi.pidissuer.port.out.registraduria.FaceMatchResult.Match(0.9),
    ): WebTestClient =
        WebTestClient
            .bindToRouterFunction(
                OnboardingOffersApi(
                    createCredentialsOffer,
                    GetOnboardingSession { session },
                    FindCedulaByDocumentNumber { found },
                    tickets,
                    apiKey,
                    compareSelfie = { _, _ -> face },
                ).router,
            ).build()

    private fun WebTestClient.offer(body: String) =
        post()
            .uri(OnboardingOffersApi.OFFERS)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()

    @Test
    fun `invalid session id is a bad request`() {
        client(OnboardingSessionResult.NotFound).offer("""{"sessionId":"x"}""").expectStatus().isBadRequest
    }

    @Test
    fun `unknown onboarding session is 404`() {
        client(OnboardingSessionResult.NotFound)
            .offer("""{"sessionId":"$sessionId"}""")
            .expectStatus().isNotFound
            .expectBody().jsonPath("$.code").isEqualTo("SESSION_NOT_FOUND")
    }

    @Test
    fun `onboarding backend down is 502`() {
        client(OnboardingSessionResult.Unavailable("x"))
            .offer("""{"sessionId":"$sessionId"}""")
            .expectStatus().isEqualTo(502)
    }

    @Test
    fun `not approved session is forbidden`() {
        val rejected = approved().copy(decision = "REJECTED")
        client(OnboardingSessionResult.Found(rejected))
            .offer("""{"sessionId":"$sessionId"}""")
            .expectStatus().isForbidden
            .expectBody().jsonPath("$.code").isEqualTo("NOT_APPROVED")
    }

    @Test
    fun `selfie of the session that does not match the registry photo is forbidden`() {
        client(
            OnboardingSessionResult.Found(approved()),
            face = eu.europa.ec.eudi.pidissuer.port.out.registraduria.FaceMatchResult.NoMatch(0.1),
        ).offer("""{"sessionId":"$sessionId","credentialFormats":["dc+sd-jwt"]}""")
            .expectStatus().isForbidden
            .expectBody().jsonPath("$.code").isEqualTo("FACE_MISMATCH")
    }

    @Test
    fun `face check unavailable fails closed with 502`() {
        client(
            OnboardingSessionResult.Found(approved()),
            face = eu.europa.ec.eudi.pidissuer.port.out.registraduria.FaceMatchResult.Unavailable("down"),
        ).offer("""{"sessionId":"$sessionId","credentialFormats":["dc+sd-jwt"]}""")
            .expectStatus().isEqualTo(502)
            .expectBody().jsonPath("$.code").isEqualTo("FACE_CHECK_UNAVAILABLE")
    }

    @Test
    fun `cedula missing from the registry is 404`() {
        client(OnboardingSessionResult.Found(approved()), found = null)
            .offer("""{"sessionId":"$sessionId"}""")
            .expectStatus().isNotFound
            .expectBody().jsonPath("$.code").isEqualTo("CEDULA_NOT_FOUND")
    }

    @Test
    fun `data that does not match the registry is forbidden with reasons`() {
        client(OnboardingSessionResult.Found(approved(birth = LocalDate(1990, 1, 1))))
            .offer("""{"sessionId":"$sessionId"}""")
            .expectStatus().isForbidden
            .expectBody()
            .jsonPath("$.code").isEqualTo("DATA_MISMATCH")
            .jsonPath("$.reasons[0]").isEqualTo("BIRTH_DATE_MISMATCH")
    }

    @Test
    fun `approved session yields only the requested PID format`() {
        val tickets = InMemoryOfferTickets(Clock.System)
        val api = client(OnboardingSessionResult.Found(approved()), tickets = tickets)
        val body = api.offer("""{"sessionId":"$sessionId","credentialFormats":["mso_mdoc"]}""")
            .expectStatus().isOk
            .expectBody(String::class.java)
            .returnResult().responseBody!!
        assertTrue("eu.europa.ec.eudi.pid_mso_mdoc" in body, body)
        assertTrue("eu.europa.ec.eudi.pid_vc_sd_jwt" !in body, body)
        assertTrue("issuer_state" in java.net.URLDecoder.decode(body, "UTF-8"), body)
    }

    @Test
    fun `approved session yields both formats when both are requested`() {
        val body = client(OnboardingSessionResult.Found(approved()))
            .offer("""{"sessionId":"$sessionId","credentialFormats":["mso_mdoc","dc+sd-jwt"]}""")
            .expectStatus().isOk
            .expectBody(String::class.java).returnResult().responseBody!!
        assertTrue("eu.europa.ec.eudi.pid_mso_mdoc" in body, body)
        assertTrue("eu.europa.ec.eudi.pid_vc_sd_jwt" in body, body)
    }

    @Test
    fun `missing or unsupported credential formats are rejected before offer creation`() {
        client(OnboardingSessionResult.Found(approved()))
            .offer("""{"sessionId":"$sessionId"}""")
            .expectStatus().isBadRequest
            .expectBody().jsonPath("$.code").isEqualTo("INVALID_CREDENTIAL_FORMATS")
        client(OnboardingSessionResult.Found(approved()))
            .offer("""{"sessionId":"$sessionId","credentialFormats":["unknown"]}""")
            .expectStatus().isBadRequest
            .expectBody().jsonPath("$.code").isEqualTo("INVALID_CREDENTIAL_FORMATS")
    }

    @Test
    fun `redeem requires the api key and consumes the ticket once`() {
        val tickets = InMemoryOfferTickets(Clock.System)
        val ticket = tickets.issue("12345678")
        val api = client(OnboardingSessionResult.NotFound, tickets = tickets)

        api.post().uri(OnboardingOffersApi.REDEEM).contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"ticket":"$ticket"}""").exchange().expectStatus().isUnauthorized

        api.post().uri(OnboardingOffersApi.REDEEM).contentType(MediaType.APPLICATION_JSON)
            .header(OnboardingOffersApi.API_KEY_HEADER, "secret")
            .bodyValue("""{"ticket":"$ticket"}""").exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.documentNumber").isEqualTo("12345678")
            .jsonPath("$.nationality").isEqualTo("CO")

        assertEquals(
            404,
            api.post().uri(OnboardingOffersApi.REDEEM).contentType(MediaType.APPLICATION_JSON)
                .header(OnboardingOffersApi.API_KEY_HEADER, "secret")
                .bodyValue("""{"ticket":"$ticket"}""").exchange().expectBody().returnResult().status.value(),
        )
    }

    @Test
    fun `redeem is disabled when no api key is configured`() {
        client(OnboardingSessionResult.NotFound, apiKey = null)
            .post().uri(OnboardingOffersApi.REDEEM).contentType(MediaType.APPLICATION_JSON)
            .header(OnboardingOffersApi.API_KEY_HEADER, "x")
            .bodyValue("""{"ticket":"t"}""").exchange().expectStatus().isEqualTo(503)
    }
}
