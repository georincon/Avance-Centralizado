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

import com.eygraber.uri.Uri
import eu.europa.ec.eudi.pidissuer.PidIssuerApplicationTest
import eu.europa.ec.eudi.pidissuer.domain.Format
import eu.europa.ec.eudi.pidissuer.domain.IssuedCredential
import eu.europa.ec.eudi.pidissuer.domain.StatusListToken
import eu.europa.ec.eudi.pidissuer.port.out.persistence.DeleteExpiredIssuedCredentials
import eu.europa.ec.eudi.pidissuer.port.out.persistence.GetActiveIssuedCredentialsBySubject
import eu.europa.ec.eudi.pidissuer.port.out.persistence.GetNonExpiredIssuedCredentials
import eu.europa.ec.eudi.pidissuer.port.out.persistence.MarkIssuedCredentialRevoked
import eu.europa.ec.eudi.pidissuer.port.out.persistence.StoreIssuedCredential
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.test.runTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.r2dbc.core.DatabaseClient
import java.util.UUID
import kotlin.jvm.optionals.getOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

@PidIssuerApplicationTest
class IssuedCredentialSubjectTest {
    @Autowired
    private lateinit var store: StoreIssuedCredential

    @Autowired
    private lateinit var getActiveBySubject: GetActiveIssuedCredentialsBySubject

    @Autowired
    private lateinit var getNonExpired: GetNonExpiredIssuedCredentials

    @Autowired
    private lateinit var markRevoked: MarkIssuedCredentialRevoked

    @Autowired
    private lateinit var deleteExpired: DeleteExpiredIssuedCredentials

    @Autowired
    private lateinit var databaseClient: DatabaseClient

    private val clock = Clock.System

    private fun credential(
        subject: String?,
        expiresInHours: Long = 24,
    ) = IssuedCredential(
        format = Format("dc+sd-jwt"),
        type = "urn:eudi:pid:1",
        issuedAt = clock.now(),
        expiresAt = clock.now() + expiresInHours.hours,
        status = StatusListToken(Uri.parse("https://example.com/status"), 3u),
        clientStatus = StatusListToken(Uri.parse("https://example.com/client"), 0u),
        keyStorageStatus = null,
        subject = subject,
    )

    /** (revoked_at, revocation_reason) del registro, o null si el registro no existe. */
    private suspend fun record(credential: IssuedCredential): Pair<Any?, String?>? =
        databaseClient
            .sql("SELECT revoked_at, revocation_reason FROM academic_credential WHERE credential_identifier = :id")
            .bind("id", UUID.fromString(credential.identifier.value.toString()))
            .map { row, _ -> row.get("revoked_at") to row.get("revocation_reason", String::class.java) }
            .first()
            .map { java.util.Optional.of(it) }
            .defaultIfEmpty(java.util.Optional.empty())
            .awaitSingle()
            .getOrNull()

    @Test
    fun `finds only the active credentials of the subject and keeps the subject`() =
        runTest {
            val titular = "titular-${UUID.randomUUID()}"
            val otro = "otro-${UUID.randomUUID()}"
            val mine = credential(titular)
            store(mine)
            store(credential(titular, expiresInHours = -1)) // vencida
            store(credential(otro))
            store(credential(subject = null))

            val found = getActiveBySubject(titular, clock.now())

            assertEquals(listOf(mine.identifier), found.map { it.identifier })
            assertEquals(titular, found.single().subject)
        }

    @Test
    fun `a revoked credential stops being active but its record is kept with date and reason`() =
        runTest {
            val titular = "titular-${UUID.randomUUID()}"
            val mine = credential(titular)
            store(mine)

            markRevoked(mine, "admin-panel")

            assertEquals(emptyList(), getActiveBySubject(titular, clock.now()))
            assertEquals(false, getNonExpired(clock.now()).any { it.identifier == mine.identifier })
            val kept = assertNotNull(record(mine), "the record must NOT be deleted")
            assertNotNull(kept.first, "revoked_at must be set")
            assertEquals("admin-panel", kept.second)
        }

    @Test
    fun `revoking twice keeps the first date and reason`() =
        runTest {
            val mine = credential("titular-${UUID.randomUUID()}")
            store(mine)
            markRevoked(mine, "primera")
            markRevoked(mine, "segunda")
            assertEquals("primera", record(mine)?.second)
        }

    @Test
    fun `an expired revoked credential is not purged, an expired active one is`() =
        runTest {
            val titular = "titular-${UUID.randomUUID()}"
            val revokedAndExpired = credential(titular, expiresInHours = -2)
            val activeAndExpired = credential(titular, expiresInHours = -2)
            store(revokedAndExpired)
            store(activeAndExpired)
            markRevoked(revokedAndExpired, "wallet-revoked")

            deleteExpired(clock.now())

            assertNotNull(record(revokedAndExpired), "a revoked record must be kept even after it expires")
            assertNull(record(activeAndExpired), "an expired credential that was never revoked is purged as before")
        }

    @Test
    fun `an unknown subject has no credentials`() =
        runTest {
            assertEquals(emptyList(), getActiveBySubject("nadie-${UUID.randomUUID()}", clock.now()))
        }
}
