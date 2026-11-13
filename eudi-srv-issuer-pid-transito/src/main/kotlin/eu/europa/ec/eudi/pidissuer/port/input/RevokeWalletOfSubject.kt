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
package eu.europa.ec.eudi.pidissuer.port.input

import eu.europa.ec.eudi.pidissuer.domain.StatusListToken
import eu.europa.ec.eudi.pidissuer.port.out.persistence.GetActiveIssuedCredentialsBySubject
import eu.europa.ec.eudi.pidissuer.port.out.status.RevokeWalletInstances
import eu.europa.ec.eudi.pidissuer.port.out.status.WalletRevocation
import kotlin.time.Clock

/**
 * Pérdida o robo del celular: revoca la **wallet** de un titular, no solo sus credenciales. El emisor guarda, por cada
 * credencial, el índice de la wallet (`clientStatus`) y el de su módulo de claves (`keyStorageStatus`); con ellos le
 * pide al proveedor de wallet que los ponga en `revoked` (ARF: WURevocation_07, WURevocation_07a). El trabajo periódico
 * de los emisores (WURevocation_18) revoca después lo que quede en esa wallet.
 *
 * Se usa en dos pasos porque, una vez revocadas las credenciales del titular, ya no figuran como activas: primero se
 * calculan las [entriesOf] y después se revoca con [invoke].
 */
class RevokeWalletOfSubject(
    private val clock: Clock,
    private val getActiveIssuedCredentialsBySubject: GetActiveIssuedCredentialsBySubject,
    private val revokeWalletInstances: RevokeWalletInstances?,
) {
    /** Entradas distintas (wallet y módulo de claves) de las credenciales vigentes del titular. */
    suspend fun entriesOf(subject: String): List<StatusListToken> =
        getActiveIssuedCredentialsBySubject(subject, clock.now())
            .flatMap { listOfNotNull(it.clientStatus, it.keyStorageStatus) }
            .distinct()

    suspend operator fun invoke(entries: List<StatusListToken>): WalletRevocation =
        when {
            revokeWalletInstances == null -> WalletRevocation(revoked = 0, failed = 0, configured = false)
            entries.isEmpty() -> WalletRevocation(revoked = 0, failed = 0)
            else -> revokeWalletInstances(entries)
        }
}
