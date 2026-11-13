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
package eu.europa.ec.eudi.pidissuer.adapter.out.persistence

import eu.europa.ec.eudi.pidissuer.port.out.persistence.BlockSubject
import eu.europa.ec.eudi.pidissuer.port.out.persistence.IsSubjectBlocked
import eu.europa.ec.eudi.pidissuer.port.out.persistence.ListBlockedSubjects
import eu.europa.ec.eudi.pidissuer.port.out.persistence.UnblockSubject
import kotlinx.coroutines.flow.toList
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.awaitOne
import org.springframework.r2dbc.core.awaitRowsUpdated
import org.springframework.r2dbc.core.flow

/**
 * Bloqueos de emisión por titular, en la tabla `blocked_subject`. Nunca toca las tablas con los datos de los titulares.
 * Un bloqueo se levanta anotando `released_at`: el historial se conserva.
 */
class R2dbcSubjectBlockRepository(
    private val db: DatabaseClient,
) {
    private suspend fun isBlocked(subject: String): Boolean =
        db
            .sql("SELECT COUNT(*) AS n FROM blocked_subject WHERE subject = :subject AND released_at IS NULL")
            .bind("subject", subject)
            .map { row, _ -> (row.get(0) as Number).toLong() }
            .awaitOne() > 0

    val isSubjectBlocked: IsSubjectBlocked = IsSubjectBlocked { isBlocked(it) }

    val blockSubject: BlockSubject =
        BlockSubject { subject, reason ->
            if (isBlocked(subject)) {
                false
            } else {
                db
                    .sql("INSERT INTO blocked_subject (subject, reason) VALUES (:subject, :reason)")
                    .bind("subject", subject)
                    .bind("reason", reason.take(255))
                    .fetch()
                    .awaitRowsUpdated()
                true
            }
        }

    val unblockSubject: UnblockSubject =
        UnblockSubject { subject ->
            db
                .sql("UPDATE blocked_subject SET released_at = CURRENT_TIMESTAMP WHERE subject = :subject AND released_at IS NULL")
                .bind("subject", subject)
                .fetch()
                .awaitRowsUpdated() > 0
        }

    val listBlockedSubjects: ListBlockedSubjects =
        ListBlockedSubjects {
            db
                .sql("SELECT DISTINCT subject FROM blocked_subject WHERE released_at IS NULL")
                .map { row, _ -> row.get("subject", String::class.java)!! }
                .flow()
                .toList()
                .toSet()
        }
}
