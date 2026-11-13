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
package eu.europa.ec.eudi.pidissuer.adapter.input.web

import eu.europa.ec.eudi.pidissuer.PidIssuerApplicationTest
import eu.europa.ec.eudi.pidissuer.adapter.out.transito.InMemoryOfferTickets
import eu.europa.ec.eudi.pidissuer.port.input.CreateCredentialsOffer
import eu.europa.ec.eudi.pidissuer.port.out.qr.GenerateQqCode
import eu.europa.ec.eudi.pidissuer.port.out.transito.FindLicenciaByDocumentNumber
import eu.europa.ec.eudi.pidissuer.port.out.transito.GetOnboardingSession
import eu.europa.ec.eudi.pidissuer.port.out.transito.OnboardingSessionResult
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.server.HandlerStrategies
import org.thymeleaf.spring6.view.reactive.ThymeleafReactiveViewResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

@PidIssuerApplicationTest
@TestPropertySource(properties = ["issuer.credentialOffer.allowedSchemes=https,openid-credential-offer,eu-eaa-offer"])
class RateLimitedOnboardingOffersTest {
    @Autowired
    private lateinit var createCredentialsOffer: CreateCredentialsOffer

    private fun client(max: Int = 2): WebTestClient =
        WebTestClient
            .bindToRouterFunction(
                OnboardingOffersApi(
                    createCredentialsOffer,
                    GetOnboardingSession { OnboardingSessionResult.NotFound },
                    FindLicenciaByDocumentNumber { null },
                    InMemoryOfferTickets(Clock.System),
                    "secret",
                    IpRateLimit(AttemptLimiter(max, 15.minutes, Clock.System), "X-Real-IP"),
                    compareSelfie = { _, _ -> eu.europa.ec.eudi.pidissuer.port.out.transito.FaceMatchResult.Match(0.9) },
                ).router,
            ).build()

    private fun WebTestClient.offerFrom(ip: String) =
        post()
            .uri(OnboardingOffersApi.OFFERS)
            .header("X-Real-IP", ip)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"sessionId":"no-es-un-uuid"}""")
            .exchange()

    @Test
    fun `an ip that exceeds the limit gets 429 with Retry-After`() {
        val client = client(max = 2)
        client.offerFrom("203.0.113.7").expectStatus().isBadRequest
        client.offerFrom("203.0.113.7").expectStatus().isBadRequest

        client
            .offerFrom("203.0.113.7")
            .expectStatus()
            .isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
            .expectHeader()
            .exists("Retry-After")
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("TOO_MANY_REQUESTS")
    }

    @Test
    fun `another ip is not affected`() {
        val client = client(max = 1)
        client.offerFrom("203.0.113.7").expectStatus().isBadRequest
        client.offerFrom("203.0.113.7").expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)

        client.offerFrom("198.51.100.2").expectStatus().isBadRequest
    }
}

@PidIssuerApplicationTest
@TestPropertySource(properties = ["issuer.credentialOffer.allowedSchemes=https,openid-credential-offer,eu-eaa-offer"])
class RateLimitedSolicitarTest {
    @Autowired
    private lateinit var createCredentialsOffer: CreateCredentialsOffer

    @Autowired
    private lateinit var generateQrCode: GenerateQqCode

    @Autowired
    private lateinit var viewResolver: ThymeleafReactiveViewResolver

    private fun client(max: Int): WebTestClient =
        WebTestClient
            .bindToRouterFunction(
                TransitoSolicitarUi(
                    createCredentialsOffer,
                    FindLicenciaByDocumentNumber { null },
                    generateQrCode,
                    IpRateLimit(AttemptLimiter(max, 15.minutes, Clock.System), "X-Real-IP"),
                    tickets = InMemoryOfferTickets(Clock.System),
                    faceMatcher = SolicitarTestSupport.face(),
                ).router,
            ).handlerStrategies(HandlerStrategies.builder().viewResolver(viewResolver).build())
            .build()

    private fun WebTestClient.solicitarFrom(ip: String) =
        post()
            .uri(TransitoSolicitarUi.SOLICITAR)
            .header("X-Real-IP", ip)
            .accept(MediaType.TEXT_HTML)
            .body(BodyInserters.fromMultipartData(SolicitarTestSupport.parts("99999999")))
            .exchange()

    @Test
    fun `an ip that exceeds the limit gets 429 with Retry-After and a clear message`() {
        val client = client(max = 2)
        client.solicitarFrom("203.0.113.7").expectStatus().isOk
        client.solicitarFrom("203.0.113.7").expectStatus().isOk

        val body =
            client
                .solicitarFrom("203.0.113.7")
                .expectStatus()
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
                .expectHeader()
                .exists("Retry-After")
                .expectBody(String::class.java)
                .returnResult()
                .responseBody
                .orEmpty()

        assertTrue(body.contains("Demasiados intentos"))
    }

    @Test
    fun `another ip is not affected`() {
        val client = client(max = 1)
        assertEquals(HttpStatus.OK, client.solicitarFrom("203.0.113.7").returnResult(String::class.java).status)
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, client.solicitarFrom("203.0.113.7").returnResult(String::class.java).status)

        client.solicitarFrom("198.51.100.2").expectStatus().isOk
    }
}
