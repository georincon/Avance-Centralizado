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

import eu.europa.ec.eudi.pidissuer.port.input.RevokeCredentialsOfSubject
import eu.europa.ec.eudi.pidissuer.port.input.SubjectControls
import eu.europa.ec.eudi.pidissuer.port.out.persistence.NoticeStatus
import eu.europa.ec.eudi.pidissuer.port.out.persistence.RevocationNotice
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

class RevocationApiControlsTest {
    private val events = mutableListOf<String>()
    private val notices = mutableListOf<RevocationNotice>()

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
                isBlocked = { false },
                block = { subject, reason ->
                    events.add("bloquear:$subject:$reason")
                    true
                },
                unblock = {
                    events.add("desbloquear:$it")
                    true
                },
                listBlocked = { emptySet() },
                listNotices = { notices.toList() },
                updateNotice = { id, status, _, _ ->
                    events.add("aviso:$id:$status")
                    id == 7L
                },
            )
        return WebTestClient.bindToRouterFunction(RevocationApi(revoke, "clave", controls).router).build()
    }

    private fun WebTestClient.post(
        path: String,
        body: String,
        key: String? = "clave",
    ) = post()
        .uri(path)
        .contentType(MediaType.APPLICATION_JSON)
        .apply { key?.let { header(RevocationApi.API_KEY_HEADER, it) } }
        .bodyValue(body)
        .exchange()

    @Test
    fun `revoking with block blocks first, then revokes, and reports it`() {
        client()
            .post(RevocationApi.REVOKE, """{"subject":"111","reason":"fraud","block":true}""")
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.blocked")
            .isEqualTo(true)
        assertEquals(listOf("bloquear:111:fraud", "revocar:111"), events)
    }

    @Test
    fun `revoking without block does not block`() {
        client()
            .post(RevocationApi.REVOKE, """{"subject":"111"}""")
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.blocked")
            .isEqualTo(false)
        assertEquals(listOf("revocar:111"), events)
    }

    @Test
    fun `block and unblock work with the key and refuse bad requests`() {
        client().post(RevocationApi.BLOCK, """{"subject":"111","reason":"legal"}""").expectStatus().isOk.expectBody().jsonPath("$.changed").isEqualTo(true)
        client().post(RevocationApi.UNBLOCK, """{"subject":"111"}""").expectStatus().isOk.expectBody().jsonPath("$.changed").isEqualTo(true)
        assertEquals(listOf("bloquear:111:legal", "desbloquear:111"), events)

        client().post(RevocationApi.BLOCK, """{"subject":"  "}""").expectStatus().isBadRequest
        client().post(RevocationApi.BLOCK, """{"subject":"111"}""", key = "otra").expectStatus().isUnauthorized
        client().post(RevocationApi.UNBLOCK, """{"subject":"111"}""", key = null).expectStatus().isUnauthorized
    }

    @Test
    fun `the open notices are listed with the key only`() {
        val now = Clock.System.now()
        notices.add(RevocationNotice(7, "111", listOf("urn:eudi:pid:1"), "fraud", "Su credencial fue revocada", now, now + 24.hours, NoticeStatus.PENDING, detail = "sin canal"))

        client()
            .get()
            .uri(RevocationApi.NOTICES)
            .header(RevocationApi.API_KEY_HEADER, "clave")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$[0].id")
            .isEqualTo(7)
            .jsonPath("$[0].subject")
            .isEqualTo("111")
            .jsonPath("$[0].status")
            .isEqualTo("PENDING")

        client().get().uri(RevocationApi.NOTICES).exchange().expectStatus().isUnauthorized
    }

    @Test
    fun `a notice can be confirmed as delivered, and an unknown one is not found`() {
        client()
            .post()
            .uri("/internal/v1/revocation-notices/7/notified")
            .header(RevocationApi.API_KEY_HEADER, "clave")
            .exchange()
            .expectStatus()
            .isOk
        client()
            .post()
            .uri("/internal/v1/revocation-notices/8/notified")
            .header(RevocationApi.API_KEY_HEADER, "clave")
            .exchange()
            .expectStatus()
            .isNotFound
        client().post().uri("/internal/v1/revocation-notices/xx/notified").header(RevocationApi.API_KEY_HEADER, "clave").exchange().expectStatus().isBadRequest
        assertEquals(listOf("aviso:7:MANUAL_DONE", "aviso:8:MANUAL_DONE"), events)
    }
}
