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
package eu.europa.ec.eudi.pidissuer.port.out.status

import eu.europa.ec.eudi.pidissuer.domain.StatusListToken

/** Resultado de pedirle al proveedor de wallet que revoque instalaciones de wallet. */
data class WalletRevocation(
    val revoked: Int,
    val failed: Int,
    /** `false` si el emisor no tiene configurado el endpoint del proveedor de wallet: no se intentó nada. */
    val configured: Boolean = true,
)

/**
 * Pide al proveedor de wallet que ponga `revoked` en las entradas de sus listas de estado que corresponden a una
 * instalación de wallet (WIA) y a su módulo de claves (KA). ARF: WURevocation_07 y WURevocation_07a.
 */
fun interface RevokeWalletInstances {
    suspend operator fun invoke(entries: List<StatusListToken>): WalletRevocation
}
