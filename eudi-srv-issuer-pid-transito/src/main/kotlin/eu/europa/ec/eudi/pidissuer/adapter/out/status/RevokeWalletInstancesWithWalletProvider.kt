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
package eu.europa.ec.eudi.pidissuer.adapter.out.status

import eu.europa.ec.eudi.pidissuer.domain.StatusListToken
import eu.europa.ec.eudi.pidissuer.port.out.status.RevokeWalletInstances
import eu.europa.ec.eudi.pidissuer.port.out.status.WalletRevocation
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import org.springframework.web.reactive.function.client.awaitExchange

private val log = LoggerFactory.getLogger(RevokeWalletInstancesWithWalletProvider::class.java)

/**
 * Llama al endpoint interno del proveedor de wallet (`POST /internal/v1/wallet-instances/revoke`).
 * Las entradas se envían en lotes de [BATCH]. Un lote que falla cuenta todas sus entradas como fallidas.
 */
internal class RevokeWalletInstancesWithWalletProvider(
    private val webClient: WebClient,
    private val url: String,
    private val apiKey: String,
) : RevokeWalletInstances {
    override suspend fun invoke(entries: List<StatusListToken>): WalletRevocation {
        var revoked = 0
        var failed = 0
        for (lote in entries.chunked(BATCH)) {
            val result = enviar(lote)
            if (result == null) {
                failed += lote.size
            } else {
                revoked += result.revoked
                failed += result.failed
            }
        }
        return WalletRevocation(revoked, failed)
    }

    private suspend fun enviar(lote: List<StatusListToken>): ResponseTO? {
        val body = Json.encodeToString(RequestTO.serializer(), RequestTO(lote.map { EntryTO(it.statusList.toString(), it.index.toLong()) }))
        return try {
            val (ok, text) =
                webClient
                    .post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers { it.set(API_KEY_HEADER, apiKey) }
                    .bodyValue(body)
                    .awaitExchange { response -> response.statusCode().is2xxSuccessful to response.awaitBody<String>() }
            if (ok) {
                lenient.decodeFromString(ResponseTO.serializer(), text)
            } else {
                log.warn("The wallet provider rejected the wallet revocation")
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("The wallet provider could not be reached to revoke wallet instances", e)
            null
        }
    }

    companion object {
        private const val API_KEY_HEADER = "X-API-Key"
        const val BATCH = 200
        private val lenient = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
private data class EntryTO(
    @SerialName("statusList") val statusList: String,
    @SerialName("index") val index: Long,
)

@Serializable
private data class RequestTO(
    @SerialName("entries") val entries: List<EntryTO>,
)

@Serializable
private data class ResponseTO(
    @SerialName("revoked") val revoked: Int = 0,
    @SerialName("failed") val failed: Int = 0,
)
