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
package eu.europa.ec.eudi.walletprovider.config

import eu.europa.ec.eudi.walletprovider.port.input.revocation.RevokeWalletInstance
import eu.europa.ec.eudi.walletprovider.port.input.revocation.StatusListEntry
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.security.MessageDigest

private val logger = LoggerFactory.getLogger("WalletInstanceRevocationRoutes")

const val MAX_REVOCATION_ENTRIES = 200
private const val API_KEY_HEADER = "X-API-Key"

/**
 * Endpoint INTERNO (solo red docker) para revocar instancias de wallet (ARF WURevocation_07 / 07a).
 *
 * - 503 si no hay clave configurada (`WALLETPROVIDER_REVOCATION_APIKEY`): el endpoint queda deshabilitado.
 * - 404 si la petición viene a través del proxy (lleva `X-Forwarded-*` / `Forwarded`): el proxy público enruta
 *   `/wallet-provider/...` hacia aquí quitando el prefijo, así que sin esta barrera `/internal/` sería alcanzable
 *   desde fuera con solo conocer la clave.
 */
fun Application.configureWalletInstanceRevocationRoutes(
    revokeWalletInstance: RevokeWalletInstance,
    apiKey: String?,
    json: Json,
) {
    val expected = apiKey?.takeIf { it.isNotBlank() }?.toByteArray(Charsets.UTF_8)
    if (expected == null) {
        logger.warn("WALLETPROVIDER_REVOCATION_APIKEY no configurada: la revocacion de instancias queda deshabilitada")
    }

    routing {
        post("/internal/v1/wallet-instances/revoke") {
            val viaProxy =
                call.request.headers.names().any {
                    it.equals("Forwarded", ignoreCase = true) || it.startsWith("X-Forwarded-", ignoreCase = true)
                }
            if (viaProxy) {
                call.respond(HttpStatusCode.NotFound)
                return@post
            }
            if (expected == null) {
                call.respond(HttpStatusCode.ServiceUnavailable)
                return@post
            }
            val provided = (call.request.headers[API_KEY_HEADER] ?: "").toByteArray(Charsets.UTF_8)
            // isEqual compara en tiempo constante para claves de igual longitud.
            if (!MessageDigest.isEqual(sha256(provided), sha256(expected))) {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }

            val request =
                try {
                    json.decodeFromString<RevocationRequest>(call.receiveText())
                } catch (_: Exception) {
                    null
                }
            if (request == null ||
                request.entries.isEmpty() ||
                request.entries.size > MAX_REVOCATION_ENTRIES ||
                request.entries.any { it.statusList.isBlank() || it.index < 0 }
            ) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            val result = revokeWalletInstance(request.entries.map { StatusListEntry(it.statusList, it.index) })
            logger.info("Revocacion de instancia de wallet: {} revocadas, {} fallidas", result.revoked, result.failed)
            call.respond(HttpStatusCode.OK, RevocationResponse(result.revoked, result.failed))
        }
    }
}

private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

@Serializable
private data class RevocationRequest(
    @Required val entries: List<RevocationEntry>,
)

@Serializable
private data class RevocationEntry(
    @Required val statusList: String,
    @Required val index: Long,
)

@Serializable
private data class RevocationResponse(
    @Required val revoked: Int,
    @Required val failed: Int,
)
