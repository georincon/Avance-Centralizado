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
import eu.europa.ec.eudi.pidissuer.adapter.out.registraduria.Cedula
import eu.europa.ec.eudi.pidissuer.adapter.out.registraduria.CedulaDocumentNumber
import eu.europa.ec.eudi.pidissuer.adapter.out.registraduria.FotoReferencia
import eu.europa.ec.eudi.pidissuer.adapter.out.registraduria.Sexo
import eu.europa.ec.eudi.pidissuer.port.input.RevokeCredentialsOfSubject
import eu.europa.ec.eudi.pidissuer.port.input.SubjectControls
import eu.europa.ec.eudi.pidissuer.port.out.persistence.NoticeStatus
import eu.europa.ec.eudi.pidissuer.port.out.persistence.RevocationNotice
import kotlinx.datetime.LocalDate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.server.HandlerStrategies
import org.thymeleaf.spring6.view.reactive.ThymeleafReactiveViewResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

/**
 * Borrar un ciudadano (función del administrador) y revocar sus credenciales son operaciones independientes:
 * borrar no revoca, y revocar nunca borra al ciudadano.
 */
@PidIssuerApplicationTest
class RegistraduriaAdminUiTest {
    @Autowired private lateinit var viewResolver: ThymeleafReactiveViewResolver

    /** Todo lo que el panel hace, en orden. */
    private val events = mutableListOf<String>()
    private val blocked = mutableSetOf<String>()
    private val notices = mutableListOf<RevocationNotice>()

    private fun cedula(doc: String) =
        Cedula(
            documentNumber = CedulaDocumentNumber(doc),
            apellidos = "MUNOZ NAVAS",
            nombres = "JOSE $doc",
            fechaNacimiento = LocalDate(1973, 9, 8),
            lugarNacimientoCiudad = "BUCARAMANGA",
            sexo = Sexo.M,
            fotoReferencia = FotoReferencia(byteArrayOf(1, 2, 3), "image/jpeg"),
        )

    private fun client(): WebTestClient {
        val revoke =
            RevokeCredentialsOfSubject(
                clock = Clock.System,
                getActiveIssuedCredentialsBySubject = { subject, _ ->
                    events.add("revocar:$subject")
                    emptyList()
                },
                markStatusAsRevoked = { },
                markIssuedCredentialRevoked = { _, _ -> },
            )
        val controls =
            SubjectControls(
                isBlocked = { blocked.contains(it) },
                block = { subject, reason ->
                    events.add("bloquear:$subject:$reason")
                    blocked.add(subject)
                },
                unblock = {
                    events.add("desbloquear:$it")
                    blocked.remove(it)
                },
                listBlocked = { blocked.toSet() },
                listNotices = { onlyOpen -> notices.filter { !onlyOpen || it.status.isOpen } },
                updateNotice = { id, status, _, _ ->
                    events.add("aviso:$id:$status")
                    true
                },
            )
        val ui =
            RegistraduriaAdminUi(
                { listOf(cedula("111"), cedula("222")) },
                { cedula(it.value) },
                { it },
                { events.add("borrar:${it.value}"); true },
                revoke,
                controls,
            )
        return WebTestClient
            .bindToRouterFunction(ui.router)
            .handlerStrategies(HandlerStrategies.builder().viewResolver(viewResolver).build())
            .build()
    }

    private fun WebTestClient.postForm(
        path: String,
        vararg fields: Pair<String, String>,
    ) = post()
        .uri(path)
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(BodyInserters.fromFormData(org.springframework.util.LinkedMultiValueMap<String, String>().also { m -> fields.forEach { m.add(it.first, it.second) } }))
        .exchange()

    private fun page(path: String): String =
        client()
            .get()
            .uri(path)
            .accept(MediaType.TEXT_HTML)
            .exchange()
            .expectStatus()
            .isOk
            .expectBody(String::class.java)
            .returnResult()
            .responseBody
            .orEmpty()

    @Test
    fun `deleting a citizen only deletes the citizen and does not touch the credentials`() {
        client().postForm("/registraduria/admin/ciudadanos/111/borrar").expectStatus().isEqualTo(303)
        assertEquals(listOf("borrar:111"), events)
    }

    @Test
    fun `revoking never deletes the citizen`() {
        client().postForm("/registraduria/admin/ciudadanos/111/revocar", "reason" to "fraud", "block" to "true").expectStatus().isEqualTo(303)
        assertTrue(events.none { it.startsWith("borrar") }, "revoking must never delete a citizen: $events")
    }

    @Test
    fun `revoking with the block option blocks first and then revokes, using the chosen reason`() {
        client()
            .postForm("/registraduria/admin/ciudadanos/111/revocar", "reason" to "fraud", "block" to "true")
            .expectStatus()
            .isEqualTo(303)
            .expectHeader()
            .value("Location") { assertTrue(it.contains("bloqueado=1"), it) }

        assertEquals(listOf("bloquear:111:fraud", "revocar:111"), events)
    }

    @Test
    fun `revoking without the block option does not block`() {
        client().postForm("/registraduria/admin/ciudadanos/111/revocar", "reason" to "user-request", "block" to "false").expectStatus().isEqualTo(303)
        assertEquals(listOf("revocar:111"), events)
    }

    @Test
    fun `an unknown reason is recorded as other`() {
        client().postForm("/registraduria/admin/ciudadanos/111/revocar", "reason" to "inventado", "block" to "true").expectStatus().isEqualTo(303)
        assertEquals(listOf("bloquear:111:other", "revocar:111"), events)
    }

    @Test
    fun `unblocking lifts the block and nothing else`() {
        blocked.add("111")
        client().postForm("/registraduria/admin/ciudadanos/111/desbloquear").expectStatus().isEqualTo(303)
        assertEquals(listOf("desbloquear:111"), events)
        assertFalse(blocked.contains("111"))
    }

    @Test
    fun `the list offers delete and revoke, and shows the block only for blocked citizens`() {
        blocked.add("111")
        val html = page("/registraduria/admin/ciudadanos")

        assertTrue(html.contains("Revocar credenciales"))
        assertTrue(html.contains("/111/borrar") && html.contains("/222/borrar"), "delete must be offered for every citizen")
        assertTrue(html.contains("Emisión bloqueada"))
        assertTrue(html.contains("/111/desbloquear"))
        assertFalse(html.contains("/222/desbloquear"), "only blocked citizens can be unblocked")
    }

    @Test
    fun `the revoke dialog asks for the reason and offers to block`() {
        val html = page("/registraduria/admin/ciudadanos")
        assertTrue(html.contains("confirm-reason") && html.contains("confirm-block"))
        assertTrue(html.contains("Posible fraude o suplantación"))
    }

    @Test
    fun `the notices page shows the pending notices with their deadline and lets the admin mark them as done`() {
        val now = Clock.System.now()
        notices.add(
            RevocationNotice(
                id = 7,
                subject = "111",
                credentialTypes = listOf("urn:eudi:pid:1"),
                reason = "fraud",
                message = "Su credencial fue revocada por fraude",
                revokedAt = now - 30.hours,
                deadlineAt = now - 6.hours,
                status = NoticeStatus.PENDING,
                detail = "el titular no tiene datos de contacto registrados",
            ),
        )
        val html = page("/registraduria/admin/avisos")

        assertTrue(html.contains("Su credencial fue revocada por fraude"))
        assertTrue(html.contains("VENCIDO"), "an overdue notice must be flagged")
        assertTrue(html.contains("el titular no tiene datos de contacto registrados"))
        assertTrue(html.contains("/avisos/7/notificado"))

        client().postForm("/registraduria/admin/avisos/7/notificado").expectStatus().isEqualTo(303)
        assertTrue(events.contains("aviso:7:MANUAL_DONE"))
    }

    @Test
    fun `the list shows how many notices are still open`() {
        val now = Clock.System.now()
        notices.add(RevocationNotice(1, "111", listOf("x"), "other", "m", now, now + 24.hours, NoticeStatus.PENDING))
        notices.add(RevocationNotice(2, "222", listOf("x"), "other", "m", now, now + 24.hours, NoticeStatus.SENT))
        val html = page("/registraduria/admin/ciudadanos")
        assertTrue(Regex("Avisos de revocación\\s*<span[^>]*>1</span>").containsMatchIn(html), "only the open notice must be counted")
    }
}
