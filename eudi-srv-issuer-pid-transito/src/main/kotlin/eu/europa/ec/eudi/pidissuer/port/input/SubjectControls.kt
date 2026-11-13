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

import eu.europa.ec.eudi.pidissuer.port.out.persistence.BlockSubject
import eu.europa.ec.eudi.pidissuer.port.out.persistence.IsSubjectBlocked
import eu.europa.ec.eudi.pidissuer.port.out.persistence.ListBlockedSubjects
import eu.europa.ec.eudi.pidissuer.port.out.persistence.ListRevocationNotices
import eu.europa.ec.eudi.pidissuer.port.out.persistence.UnblockSubject
import eu.europa.ec.eudi.pidissuer.port.out.persistence.UpdateRevocationNotice

/**
 * Las operaciones de administración sobre los titulares (bloqueo de emisión y avisos de revocación) que comparten
 * el panel y la API interna.
 */
class SubjectControls(
    val isBlocked: IsSubjectBlocked,
    val block: BlockSubject,
    val unblock: UnblockSubject,
    val listBlocked: ListBlockedSubjects,
    val listNotices: ListRevocationNotices,
    val updateNotice: UpdateRevocationNotice,
) {
    companion object {
        /** Sin bloqueos ni avisos: para pruebas. */
        fun none(): SubjectControls =
            SubjectControls(
                isBlocked = { false },
                block = { _, _ -> false },
                unblock = { false },
                listBlocked = { emptySet() },
                listNotices = { emptyList() },
                updateNotice = { _, _, _, _ -> false },
            )
    }
}
