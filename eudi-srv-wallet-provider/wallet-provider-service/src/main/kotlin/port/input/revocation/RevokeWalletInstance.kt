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
package eu.europa.ec.eudi.walletprovider.port.input.revocation

import eu.europa.ec.eudi.walletprovider.port.output.tokenstatuslist.RevokeStatusListToken
import org.slf4j.LoggerFactory

/**
 * Entrada de una lista de estado a revocar: URI pública de la lista e índice dentro de ella.
 */
data class StatusListEntry(
    val statusList: String,
    val index: Long,
)

data class WalletInstanceRevocationResult(
    val revoked: Int,
    val failed: Int,
)

/**
 * Revoca una Wallet Instance (ARF WURevocation_07 / 07a) marcando como `revoked` los índices de las listas de estado
 * de sus WIA y de sus KA. Cada entrada es independiente: una que falle no detiene las demás. Es idempotente.
 */
fun interface RevokeWalletInstance {
    suspend operator fun invoke(entries: List<StatusListEntry>): WalletInstanceRevocationResult
}

class RevokeWalletInstanceLive(
    private val revokeStatusListToken: RevokeStatusListToken,
) : RevokeWalletInstance {
    override suspend operator fun invoke(entries: List<StatusListEntry>): WalletInstanceRevocationResult {
        var revoked = 0
        var failed = 0
        entries.distinct().forEach { entry ->
            try {
                revokeStatusListToken(entry.statusList, entry.index)
                revoked++
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                failed++
                logger.warn("No se pudo revocar el indice {} de {}: {}", entry.index, entry.statusList, e.message)
            }
        }
        return WalletInstanceRevocationResult(revoked, failed)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RevokeWalletInstanceLive::class.java)
    }
}
