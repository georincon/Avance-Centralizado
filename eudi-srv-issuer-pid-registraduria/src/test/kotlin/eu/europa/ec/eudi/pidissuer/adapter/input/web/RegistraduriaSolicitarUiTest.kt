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

import eu.europa.ec.eudi.pidissuer.adapter.out.registraduria.InMemoryOfferTickets
import kotlin.time.Clock
import eu.europa.ec.eudi.pidissuer.PidIssuerApplicationTest
import eu.europa.ec.eudi.pidissuer.adapter.out.registraduria.Cedula
import eu.europa.ec.eudi.pidissuer.adapter.out.registraduria.CedulaDocumentNumber
import eu.europa.ec.eudi.pidissuer.adapter.out.registraduria.FotoReferencia
import eu.europa.ec.eudi.pidissuer.adapter.out.registraduria.Sexo
import eu.europa.ec.eudi.pidissuer.port.input.CreateCredentialsOffer
import eu.europa.ec.eudi.pidissuer.port.out.qr.GenerateQqCode
import eu.europa.ec.eudi.pidissuer.port.out.registraduria.FindCedulaByDocumentNumber
import kotlinx.datetime.LocalDate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.server.HandlerStrategies
import org.thymeleaf.spring6.view.reactive.ThymeleafReactiveViewResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@PidIssuerApplicationTest
@TestPropertySource(properties = ["issuer.credentialOffer.allowedSchemes=https,openid-credential-offer,eu-eaa-offer"])
class RegistraduriaSolicitarUiTest {
    @Autowired
    private lateinit var createCredentialsOffer: CreateCredentialsOffer

    @Autowired
    private lateinit var generateQrCode: GenerateQqCode

    private val complete =
        Cedula(
            documentNumber = CedulaDocumentNumber("12345678"),
            apellidos = "MUNOZ NAVAS",
            nombres = "JOSE GREGORIO",
            fechaNacimiento = LocalDate(1973, 9, 8),
            lugarNacimientoCiudad = "BUCARAMANGA",
            sexo = Sexo.M,
            fotoReferencia = FotoReferencia(byteArrayOf(1, 2, 3), "image/jpeg"),
        )

    @Autowired
    private lateinit var viewResolver: ThymeleafReactiveViewResolver

    private fun client(found: Cedula?): WebTestClient =
        WebTestClient
            .bindToRouterFunction(
                RegistraduriaSolicitarUi(createCredentialsOffer, FindCedulaByDocumentNumber { found }, generateQrCode, tickets = InMemoryOfferTickets(Clock.System), faceMatcher = SolicitarTestSupport.face()).router,
            ).handlerStrategies(HandlerStrategies.builder().viewResolver(viewResolver).build())
            .build()

    private fun WebTestClient.solicitar(documentNumber: String): String =
        post()
            .uri(RegistraduriaSolicitarUi.SOLICITAR)
            .accept(MediaType.TEXT_HTML)
            .body(BodyInserters.fromMultipartData(SolicitarTestSupport.parts(documentNumber)))
            .exchange()
            .expectStatus()
            .isOk
            .expectBody(String::class.java)
            .returnResult()
            .responseBody
            .orEmpty()

    @Test
    fun `unknown and incomplete cedulas get the same response`() {
        val unknown = client(found = null).solicitar("99999999")
        val incomplete = client(found = complete.copy(fotoReferencia = null)).solicitar("12345678")

        assertTrue(unknown.contains(RegistraduriaSolicitarUi.GENERIC_ERROR))
        assertTrue(incomplete.contains(RegistraduriaSolicitarUi.GENERIC_ERROR))
        assertFalse(incomplete.contains("falta la foto"), "the reason must not be disclosed")
    }

    @Test
    fun `a complete cedula still gets its offer`() {
        val page = client(found = complete).solicitar("12345678")

        assertFalse(page.contains(RegistraduriaSolicitarUi.GENERIC_ERROR))
        assertTrue(page.contains("credential_offer"), "the page must show the credential offer")
        assertTrue(page.contains("Compartir con mi wallet"), "en el celular debe poder abrir la wallet directamente")
        assertTrue(page.contains("abrir-wallet.js"))
    }

    private fun WebTestClient.solicitarConFormatos(
        documentNumber: String,
        vararg formats: String,
    ): String =
        post()
            .uri(RegistraduriaSolicitarUi.SOLICITAR)
            .accept(MediaType.TEXT_HTML)
            .body(BodyInserters.fromMultipartData(SolicitarTestSupport.parts(documentNumber, formats.toList()))).exchange()
            .expectStatus()
            .isOk
            .expectBody(String::class.java)
            .returnResult()
            .responseBody
            .orEmpty()

    @Test
    fun `only the chosen format is offered`() {
        val mdoc = client(found = complete).solicitarConFormatos("12345678", "mdoc")
        val sdjwt = client(found = complete).solicitarConFormatos("12345678", "sdjwt")
        val ambos = client(found = complete).solicitarConFormatos("12345678", "mdoc", "sdjwt")

        assertTrue(mdoc.contains("pid_mso_mdoc") && !mdoc.contains("pid_vc_sd_jwt"), "solo mDoc")
        assertTrue(sdjwt.contains("pid_vc_sd_jwt") && !sdjwt.contains("pid_mso_mdoc"), "solo SD-JWT VC")
        assertTrue(ambos.contains("pid_mso_mdoc") && ambos.contains("pid_vc_sd_jwt"), "los dos")
    }

    @Test
    fun `choosing no format asks to choose at least one and keeps the form`() {
        val page = client(found = complete).solicitarConFormatos("12345678")
        assertTrue(page.contains("Elige al menos un formato"))
        assertFalse(page.contains("credential_offer"))
    }

    @Test
    fun `the form shows the two formats as checkboxes`() {
        val page =
            client(found = complete)
                .get()
                .uri(RegistraduriaSolicitarUi.SOLICITAR)
                .accept(MediaType.TEXT_HTML)
                .exchange()
                .expectStatus()
                .isOk
                .expectBody(String::class.java)
                .returnResult()
                .responseBody
                .orEmpty()
        assertTrue(page.contains("value=\"mdoc\"") && page.contains("value=\"sdjwt\""))
        assertFalse(page.contains("checked"), "al entrar no debe haber ningún formato marcado: hay que elegir")
        assertTrue(page.contains("registraduria-solicitar.js"))
    }

    @Test
    fun `a blank cedula is rejected`() {
        val page = client(found = complete).solicitar("   ")
        assertTrue(page.contains("Debes ingresar tu número de cédula"))
    }

    private fun clientWith(
        face: eu.europa.ec.eudi.pidissuer.port.out.registraduria.FaceMatcher,
    ): WebTestClient =
        WebTestClient
            .bindToRouterFunction(
                RegistraduriaSolicitarUi(
                    createCredentialsOffer,
                    FindCedulaByDocumentNumber { complete },
                    generateQrCode,
                    tickets = InMemoryOfferTickets(Clock.System),
                    faceMatcher = face,
                ).router,
            ).handlerStrategies(HandlerStrategies.builder().viewResolver(viewResolver).build())
            .build()

    private fun WebTestClient.post(parts: org.springframework.util.MultiValueMap<String, *>): String =
        post()
            .uri(RegistraduriaSolicitarUi.SOLICITAR)
            .accept(MediaType.TEXT_HTML)
            .body(BodyInserters.fromMultipartData(parts))
            .exchange()
            .expectBody(String::class.java)
            .returnResult()
            .responseBody
            .orEmpty()

    @Test
    fun `a selfie that does not match the registry photo gets the generic error and no offer`() {
        val page =
            clientWith(SolicitarTestSupport.face(match = eu.europa.ec.eudi.pidissuer.port.out.registraduria.FaceMatchResult.NoMatch(0.1)))
                .post(SolicitarTestSupport.parts("12345678"))
        assertTrue(page.contains(RegistraduriaSolicitarUi.GENERIC_ERROR))
        assertFalse(page.contains("credential_offer"))
    }

    @Test
    fun `declared data that differs from the registry gets the generic error and no offer`() {
        val page = clientWith(SolicitarTestSupport.face()).post(SolicitarTestSupport.parts("12345678", birthDate = "1980-01-01"))
        assertTrue(page.contains(RegistraduriaSolicitarUi.GENERIC_ERROR))
        assertFalse(page.contains("credential_offer"))
    }

    @Test
    fun `a verified request gets an offer that carries a one-time issuer_state ticket`() {
        val page = clientWith(SolicitarTestSupport.face()).post(SolicitarTestSupport.parts("12345678"))
        assertTrue(page.contains("issuer_state"), "the offer must carry the ticket that Keycloak needs to authenticate")
    }

    @Test
    fun `without a selfie nothing is offered`() {
        val page = clientWith(SolicitarTestSupport.face()).post(SolicitarTestSupport.parts("12345678", withSelfie = false))
        assertFalse(page.contains("credential_offer"))
    }

}

@PidIssuerApplicationTest
class RegistraduriaSolicitarUiEnabledByDefaultTest {
    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Test
    fun `the page is available when the property is not set`() {
        WebTestClient
            .bindToApplicationContext(applicationContext)
            .configureClient()
            .build()
            .get()
            .uri(RegistraduriaSolicitarUi.SOLICITAR)
            .accept(MediaType.TEXT_HTML)
            .exchange()
            .expectStatus()
            .isOk
    }
}

@PidIssuerApplicationTest
@TestPropertySource(properties = ["${RegistraduriaSolicitarUi.ENABLED_PROPERTY}=false"])
class RegistraduriaSolicitarUiDisabledTest {
    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Test
    fun `the page does not exist when the property is false`() {
        val status =
            WebTestClient
                .bindToApplicationContext(applicationContext)
                .configureClient()
                .build()
                .get()
                .uri(RegistraduriaSolicitarUi.SOLICITAR)
                .accept(MediaType.TEXT_HTML)
                .exchange()
                .returnResult(String::class.java)
                .status

        assertEquals(404, status.value())
    }

}
