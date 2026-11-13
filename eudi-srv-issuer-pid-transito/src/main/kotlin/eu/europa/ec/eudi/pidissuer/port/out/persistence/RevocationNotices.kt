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
package eu.europa.ec.eudi.pidissuer.port.out.persistence

import kotlin.time.Instant

enum class NoticeStatus {
    /** Registrado, todavía sin entregar (por ejemplo, sin datos de contacto o sin canal): hay que avisar a mano. */
    PENDING,

    /** Entregado por un canal automático. */
    SENT,

    /** El administrador confirmó que avisó al titular por su cuenta. */
    MANUAL_DONE,

    /** El canal automático falló: sigue pendiente. */
    FAILED,
    ;

    val isOpen: Boolean get() = this == PENDING || this == FAILED
}

/**
 * Aviso al titular de que una credencial suya fue revocada. El CIR 2024/2977 art. 5(3) exige informar al titular, por
 * un canal seguro y dentro de las 24 horas siguientes a la revocación, de la revocación y de sus motivos.
 */
data class RevocationNotice(
    val id: Long? = null,
    val subject: String,
    val credentialTypes: List<String>,
    val reason: String,
    val message: String,
    val revokedAt: Instant,
    /** Límite para avisar: `revokedAt` + 24 horas. */
    val deadlineAt: Instant,
    val status: NoticeStatus = NoticeStatus.PENDING,
    val channel: String? = null,
    val notifiedAt: Instant? = null,
    val detail: String? = null,
)

fun interface StoreRevocationNotice {
    /** Guarda el aviso y devuelve su id. */
    suspend operator fun invoke(notice: RevocationNotice): Long
}

fun interface ListRevocationNotices {
    /** Con `onlyOpen`, solo los avisos sin entregar (más urgentes primero); si no, los 200 más recientes. */
    suspend operator fun invoke(onlyOpen: Boolean): List<RevocationNotice>
}

fun interface UpdateRevocationNotice {
    suspend operator fun invoke(
        id: Long,
        status: NoticeStatus,
        channel: String?,
        detail: String?,
    ): Boolean
}
