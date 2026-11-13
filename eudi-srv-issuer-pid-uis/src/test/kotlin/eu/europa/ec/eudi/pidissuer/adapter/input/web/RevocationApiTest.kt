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

import com.eygraber.uri.Uri
import eu.europa.ec.eudi.pidissuer.domain.Format
import eu.europa.ec.eudi.pidissuer.domain.IssuedCredential
import eu.europa.ec.eudi.pidissuer.domain.StatusListToken
import eu.europa.ec.eudi.pidissuer.port.input.RevokeCredentialsOfSubject
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

class RevocationApiTest {
    private val requested = mutableListOf<String>()

    private fun client(apiKey: String? = "secreta"): WebTestClient {
        val useCase =
            RevokeCredentialsOfSubject(
                clock = Clock.System,
                getActiveIssuedCredentialsBySubject = { subject, _ ->
                    requested.add(subject)
                    listOf(
                        IssuedCredential(
                            format = Format("dc+sd-jwt"),
                            type = "urn:eudi:pid:1",
                            issuedAt = Clock.System.now(),
                            expiresAt = Clock.System.now() + 24.hours,
                            status = StatusListToken(Uri.parse("https://example.com/status"), 7u),
                            clientStatus = StatusListToken(Uri.parse("https://example.com/client"), 0u),
                            keyStorageStatus = null,
                        ),
                    )
                },
                markStatusAsRevoked = { },
                markIssuedCredentialRevoked = { _, _ -> },
            )
        return WebTestClient.bindToRouterFunction(RevocationApi(useCase, apiKey).router).build()
    }

    private fun WebTestClient.revoke(
        body: String,
        apiKey: String? = "secreta",
    ) = post()
        .uri(RevocationApi.REVOKE)
        .contentType(MediaType.APPLICATION_JSON)
        .apply { apiKey?.let { header(RevocationApi.API_KEY_HEADER, it) } }
        .bodyValue(body)
        .exchange()

    @Test
    fun `revokes the credentials of the subject and reports the result`() {
        client()
            .revoke("""{"subject":"12345678"}""")
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.revoked")
            .isEqualTo(1)
            .jsonPath("$.failed")
            .isEqualTo(0)
            .jsonPath("$.withoutStatus")
            .isEqualTo(0)
        assertEquals(listOf("12345678"), requested)
    }

    @Test
    fun `a wrong or missing api key is rejected without revoking anything`() {
        client().revoke("""{"subject":"12345678"}""", apiKey = "otra").expectStatus().isUnauthorized
        client().revoke("""{"subject":"12345678"}""", apiKey = null).expectStatus().isUnauthorized
        assertEquals(emptyList(), requested)
    }

    @Test
    fun `without a configured api key the api is disabled`() {
        client(apiKey = null).revoke("""{"subject":"12345678"}""").expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
        client(apiKey = "").revoke("""{"subject":"12345678"}""").expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
        assertEquals(emptyList(), requested)
    }

    @Test
    fun `a blank, missing, malformed or oversized subject is a bad request`() {
        client().revoke("""{"subject":"   "}""").expectStatus().isBadRequest
        client().revoke("""{}""").expectStatus().isBadRequest
        client().revoke("""no es json""").expectStatus().isBadRequest
        client().revoke("""{"subject":"${"x".repeat(300)}"}""").expectStatus().isBadRequest
        assertEquals(emptyList(), requested)
    }
}
