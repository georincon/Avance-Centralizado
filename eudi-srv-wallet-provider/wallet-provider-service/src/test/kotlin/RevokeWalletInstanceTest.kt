/*
 * Copyright (c) 2025-2026 European Commission
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
package eu.europa.ec.eudi.walletprovider

import com.eygraber.uri.Url
import eu.europa.ec.eudi.walletprovider.domain.toNonBlankString
import eu.europa.ec.eudi.walletprovider.adapter.tokenstatuslist.RevokeStatusListToken
import eu.europa.ec.eudi.walletprovider.adapter.tokenstatuslist.statusListServiceUri
import eu.europa.ec.eudi.walletprovider.config.configureWalletInstanceRevocationRoutes
import eu.europa.ec.eudi.walletprovider.port.input.revocation.RevokeWalletInstanceLive
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import io.ktor.client.HttpClient as KtorClient

class RevokeWalletInstanceTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val publicList = "https://civica-desarrollo.avance.org.co/status-list/wallet-provider/token_status_list/FC/x/abc.jwt"
    private val calls = mutableListOf<Pair<HttpRequestData, Parameters>>()

    private fun statusServiceClient(failWhen: (Parameters) -> Boolean = { false }) =
        KtorClient(
            MockEngine { request ->
                val form = (request.body as FormDataContent).formData
                calls += request to form
                if (failWhen(form)) respondError(HttpStatusCode.InternalServerError) else respondOk()
            },
        )

    private fun withApp(
        apiKey: String?,
        client: KtorClient = statusServiceClient(),
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        application {
            install(ContentNegotiation) { json(json) }
            val revoke =
                RevokeWalletInstanceLive(
                    RevokeStatusListToken(client, Url.parse("http://status-list-spartans:5000/token_status_list/take"), "svc-key".toNonBlankString()),
                )
            configureWalletInstanceRevocationRoutes(revoke, apiKey, json)
        }
        block()
    }

    private suspend fun ApplicationTestBuilder.post(
        body: String,
        key: String? = "k",
        extra: HttpRequestBuilder.() -> Unit = {},
    ) = client.post("/internal/v1/wallet-instances/revoke") {
        contentType(ContentType.Application.Json)
        key?.let { header("X-API-Key", it) }
        setBody(body)
        extra()
    }

    private val ok = """{"entries":[{"statusList":"$publicList","index":7},{"statusList":"$publicList","index":8}]}"""

    @Test
    fun `revoca cada entrada llamando a set con la ruta recortada`() =
        withApp("k") {
            val r = post(ok)
            assertEquals(HttpStatusCode.OK, r.status)
            assertEquals("""{"revoked":2,"failed":0}""", r.bodyAsText().replace(" ", "").replace("\n", ""))
            assertEquals(2, calls.size)
            val (req, form) = calls[0]
            assertEquals("http://status-list-spartans:5000/token_status_list/set", req.url.toString())
            assertEquals("svc-key", req.headers["X-API-Key"])
            assertEquals("7", form["idx"])
            assertEquals("/token_status_list/FC/x/abc.jwt", form["uri"])
            assertEquals("1", form["status"])
        }

    @Test
    fun `una entrada fallida cuenta como failed y no detiene las demas`() =
        withApp("k", statusServiceClient { it["idx"] == "7" }) {
            val r = post(ok)
            assertEquals(HttpStatusCode.OK, r.status)
            assertTrue(r.bodyAsText().contains("\"revoked\": 1") || r.bodyAsText().contains("\"revoked\":1"))
            assertTrue(r.bodyAsText().contains("\"failed\": 1") || r.bodyAsText().contains("\"failed\":1"))
            assertEquals(2, calls.size)
        }

    @Test
    fun `entrada con URI ajena al servicio cuenta como failed`() =
        withApp("k") {
            val r = post("""{"entries":[{"statusList":"https://x.org/otra/cosa","index":1}]}""")
            assertEquals(HttpStatusCode.OK, r.status)
            assertTrue(r.bodyAsText().replace(" ", "").contains("\"failed\":1"))
            assertTrue(calls.isEmpty())
        }

    @Test
    fun `clave incorrecta o ausente da 401`() =
        withApp("k") {
            assertEquals(HttpStatusCode.Unauthorized, post(ok, key = "otra").status)
            assertEquals(HttpStatusCode.Unauthorized, post(ok, key = null).status)
            assertTrue(calls.isEmpty())
        }

    @Test
    fun `sin clave configurada da 503`() {
        withApp(null) { assertEquals(HttpStatusCode.ServiceUnavailable, post(ok).status) }
        withApp("  ") { assertEquals(HttpStatusCode.ServiceUnavailable, post(ok).status) }
    }

    @Test
    fun `peticiones invalidas dan 400`() =
        withApp("k") {
            assertEquals(HttpStatusCode.BadRequest, post("no es json").status)
            assertEquals(HttpStatusCode.BadRequest, post("""{"entries":[]}""").status)
            assertEquals(HttpStatusCode.BadRequest, post("""{}""").status)
            assertEquals(HttpStatusCode.BadRequest, post("""{"entries":[{"statusList":"","index":1}]}""").status)
            assertEquals(HttpStatusCode.BadRequest, post("""{"entries":[{"statusList":"$publicList","index":-1}]}""").status)
            val many = (1..201).joinToString(",") { """{"statusList":"$publicList","index":$it}""" }
            assertEquals(HttpStatusCode.BadRequest, post("""{"entries":[$many]}""").status)
            val max = (1..200).joinToString(",") { """{"statusList":"$publicList","index":$it}""" }
            assertEquals(HttpStatusCode.OK, post("""{"entries":[$max]}""").status)
        }

    @Test
    fun `peticion a traves del proxy no es alcanzable`() =
        withApp("k") {
            assertEquals(HttpStatusCode.NotFound, post(ok) { header("X-Forwarded-For", "1.2.3.4") }.status)
            assertEquals(HttpStatusCode.NotFound, post(ok) { header("Forwarded", "for=1.2.3.4") }.status)
            assertTrue(calls.isEmpty())
        }

    @Test
    fun `recorte de URI publica`() {
        assertEquals("/token_status_list/FC/x/abc.jwt", statusListServiceUri(publicList))
        assertEquals("/token_status_list/FC/x/abc.jwt", statusListServiceUri("/token_status_list/FC/x/abc.jwt"))
        assertFailsWith<IllegalArgumentException> { statusListServiceUri("https://x.org/otra") }
    }
}
