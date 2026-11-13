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
package eu.europa.ec.eudi.pidissuer.port.out.notification

import eu.europa.ec.eudi.pidissuer.port.out.persistence.RevocationNotice

/** Datos para localizar al titular. Solo de lectura: se leen donde ya existan, nunca se piden ni se modifican. */
data class SubjectContact(
    val email: String?,
    val phone: String?,
) {
    val isEmpty: Boolean get() = email.isNullOrBlank() && phone.isNullOrBlank()
}

fun interface GetSubjectContact {
    suspend operator fun invoke(subject: String): SubjectContact?
}

sealed interface DeliveryResult {
    data class Sent(
        val channel: String,
    ) : DeliveryResult

    /** No hay forma de entregarlo automáticamente: el aviso queda pendiente para hacerlo a mano. */
    data class NoChannel(
        val reason: String,
    ) : DeliveryResult

    data class Failed(
        val reason: String,
    ) : DeliveryResult
}

fun interface DeliverRevocationNotice {
    suspend operator fun invoke(
        notice: RevocationNotice,
        contact: SubjectContact?,
    ): DeliveryResult
}
