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

import eu.europa.ec.eudi.pidissuer.port.out.persistence.ListRevocationNotices
import eu.europa.ec.eudi.pidissuer.port.out.persistence.NoticeStatus
import eu.europa.ec.eudi.pidissuer.port.out.persistence.RevocationNotice
import eu.europa.ec.eudi.pidissuer.port.out.persistence.StoreRevocationNotice
import eu.europa.ec.eudi.pidissuer.port.out.persistence.UpdateRevocationNotice
import io.r2dbc.spi.Row
import kotlinx.coroutines.flow.toList
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.awaitOne
import org.springframework.r2dbc.core.awaitRowsUpdated
import org.springframework.r2dbc.core.flow
import java.time.OffsetDateTime
import kotlin.time.toKotlinInstant

/**
 * Avisos de revocación al titular, en la tabla `revocation_notice`. Los avisos no se borran.
 */
class R2dbcRevocationNoticeRepository(
    private val db: DatabaseClient,
) {
    val storeRevocationNotice: StoreRevocationNotice =
        StoreRevocationNotice { notice ->
            db
                .sql(
                    """
                    INSERT INTO revocation_notice (subject, credential_types, reason, message, revoked_at, deadline_at, status)
                    VALUES (:subject, :types, :reason, :message, :revokedAt, :deadlineAt, :status)
                    """.trimIndent(),
                ).bind("subject", notice.subject)
                .bind("types", notice.credentialTypes.joinToString(",").take(1000))
                .bind("reason", notice.reason.take(255))
                .bind("message", notice.message.take(2000))
                .bind("revokedAt", notice.revokedAt.toOffsetDateTime())
                .bind("deadlineAt", notice.deadlineAt.toOffsetDateTime())
                .bind("status", notice.status.name)
                .filter { statement -> statement.returnGeneratedValues("id") }
                .map { row, _ -> (row.get("id") as Number).toLong() }
                .awaitOne()
        }

    val listRevocationNotices: ListRevocationNotices =
        ListRevocationNotices { onlyOpen ->
            val tail =
                if (onlyOpen) {
                    "WHERE status IN ('PENDING', 'FAILED') ORDER BY deadline_at ASC"
                } else {
                    "ORDER BY revoked_at DESC LIMIT 200"
                }
            db
                .sql(
                    "SELECT id, subject, credential_types, reason, message, revoked_at, deadline_at, status, channel, " +
                        "notified_at, detail FROM revocation_notice $tail",
                ).map { row, _ -> row.toNotice() }
                .flow()
                .toList()
        }

    val updateRevocationNotice: UpdateRevocationNotice =
        UpdateRevocationNotice { id, status, channel, detail ->
            val delivered = status == NoticeStatus.SENT || status == NoticeStatus.MANUAL_DONE
            val sql =
                "UPDATE revocation_notice SET status = :status, channel = :channel, detail = :detail" +
                    (if (delivered) ", notified_at = CURRENT_TIMESTAMP" else "") +
                    " WHERE id = :id"
            db
                .sql(sql)
                .bind("status", status.name)
                .bindNullable("channel", channel?.take(100))
                .bindNullable("detail", detail?.take(500))
                .bind("id", id)
                .fetch()
                .awaitRowsUpdated() > 0
        }
}

private fun DatabaseClient.GenericExecuteSpec.bindNullable(
    name: String,
    value: String?,
): DatabaseClient.GenericExecuteSpec = if (value == null) bindNull(name, String::class.java) else bind(name, value)

private fun Row.toNotice(): RevocationNotice =
    RevocationNotice(
        id = (get("id") as Number).toLong(),
        subject = get("subject", String::class.java)!!,
        credentialTypes = get("credential_types", String::class.java)!!.split(",").filter { it.isNotBlank() },
        reason = get("reason", String::class.java)!!,
        message = get("message", String::class.java)!!,
        revokedAt = get("revoked_at", OffsetDateTime::class.java)!!.toInstant().toKotlinInstant(),
        deadlineAt = get("deadline_at", OffsetDateTime::class.java)!!.toInstant().toKotlinInstant(),
        status = NoticeStatus.valueOf(get("status", String::class.java)!!),
        channel = get("channel", String::class.java),
        notifiedAt = get("notified_at", OffsetDateTime::class.java)?.toInstant()?.toKotlinInstant(),
        detail = get("detail", String::class.java),
    )
