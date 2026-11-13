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
import eu.europa.ec.eudi.pidissuer.adapter.out.transito.Licencia
import eu.europa.ec.eudi.pidissuer.adapter.out.transito.licenciaDePrueba
import eu.europa.ec.eudi.pidissuer.adapter.out.transito.LicenciaDocumentNumber
import eu.europa.ec.eudi.pidissuer.adapter.out.transito.FotoReferencia
import eu.europa.ec.eudi.pidissuer.adapter.out.transito.InMemoryOfferTickets
import eu.europa.ec.eudi.pidissuer.adapter.out.transito.Sexo
import eu.europa.ec.eudi.pidissuer.port.input.CreateCredentialsOffer
import eu.europa.ec.eudi.pidissuer.port.out.qr.GenerateQqCode
import eu.europa.ec.eudi.pidissuer.port.out.transito.OnboardingSession
import eu.europa.ec.eudi.pidissuer.port.out.transito.OnboardingSessionResult
import kotlinx.datetime.LocalDate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.server.HandlerStrategies
import org.thymeleaf.spring6.view.reactive.ThymeleafReactiveViewResolver
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * Un ciudadano con la emisión bloqueada no puede obtener una oferta de credencial por ninguna de las dos puertas
 * públicas (onboarding y pantalla ciudadana). Sus datos siguen en la base.
 */
@PidIssuerApplicationTest
@TestPropertySource(properties = ["issuer.credentialOffer.allowedSchemes=https,openid-credential-offer,eu-eaa-offer"])
class BlockedSubjectIssuanceTest {
    @Autowired private lateinit var createCredentialsOffer: CreateCredentialsOffer

    @Autowired private lateinit var generateQrCode: GenerateQqCode

    @Autowired private lateinit var viewResolver: ThymeleafReactiveViewResolver

    private val licencia =
        licenciaDePrueba(doc = "12345678")

    private fun offers(blocked: Boolean): WebTestClient =
        WebTestClient
            .bindToRouterFunction(
                OnboardingOffersApi(
                    createCredentialsOffer,
                    {
                        OnboardingSessionResult.Found(
                            OnboardingSession("APPROVED", "COMPLETED", "12345678", "JOSE GREGORIO", "MUNOZ NAVAS", LocalDate(1973, 9, 8)),
                        )
                    },
                    { licencia },
                    InMemoryOfferTickets(Clock.System),
                    "secret",
                    isSubjectBlocked = { blocked },
                    compareSelfie = { _, _ -> eu.europa.ec.eudi.pidissuer.port.out.transito.FaceMatchResult.Match(0.9) },
                ).router,
            ).build()

    private fun WebTestClient.askOffer() =
        post()
            .uri(OnboardingOffersApi.OFFERS)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"sessionId":"${UUID.randomUUID()}","credentialFormats":["mso_mdoc"]}""")
            .exchange()

    @Test
    fun `onboarding refuses the offer to a blocked citizen`() {
        offers(blocked = true)
            .askOffer()
            .expectStatus()
            .isEqualTo(HttpStatus.FORBIDDEN)
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("SUBJECT_BLOCKED")
    }

    @Test
    fun `onboarding still gives the offer to a citizen who is not blocked`() {
        offers(blocked = false).askOffer().expectStatus().isOk
    }

    private fun screen(blocked: Boolean): WebTestClient =
        WebTestClient
            .bindToRouterFunction(
                TransitoSolicitarUi(createCredentialsOffer, { licencia }, generateQrCode, isSubjectBlocked = { blocked }, tickets = InMemoryOfferTickets(Clock.System), faceMatcher = SolicitarTestSupport.face()).router,
            ).handlerStrategies(HandlerStrategies.builder().viewResolver(viewResolver).build())
            .build()

    private fun WebTestClient.solicitar(): String =
        post()
            .uri(TransitoSolicitarUi.SOLICITAR)
            .accept(MediaType.TEXT_HTML)
            .body(BodyInserters.fromMultipartData(SolicitarTestSupport.parts("12345678")))
            .exchange()
            .expectStatus()
            .isOk
            .expectBody(String::class.java)
            .returnResult()
            .responseBody
            .orEmpty()

    @Test
    fun `the citizen screen gives a blocked citizen the generic error and no offer`() {
        val page = screen(blocked = true).solicitar()
        assertTrue(page.contains(TransitoSolicitarUi.GENERIC_ERROR))
        assertFalse(page.contains("credential_offer"))
    }

    @Test
    fun `the citizen screen still gives the offer to a citizen who is not blocked`() {
        val page = screen(blocked = false).solicitar()
        assertFalse(page.contains(TransitoSolicitarUi.GENERIC_ERROR))
        assertTrue(page.contains("credential_offer"))
    }
}
