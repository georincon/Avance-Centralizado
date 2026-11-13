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

import arrow.core.raise.context.raise
import com.eygraber.uri.Uri
import eu.europa.ec.eudi.pidissuer.domain.Format
import eu.europa.ec.eudi.pidissuer.domain.IssuedCredential
import eu.europa.ec.eudi.pidissuer.domain.StatusListToken
import eu.europa.ec.eudi.pidissuer.port.out.status.MarkStatusAsRevoked
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

internal class RevokeCredentialsOfSubjectTest {
    private val clock = Clock.System

    private fun credential(
        index: UInt,
        withStatus: Boolean = true,
    ) = IssuedCredential(
        format = Format("dc+sd-jwt"),
        type = "urn:eudi:pid:1",
        issuedAt = clock.now(),
        expiresAt = clock.now() + 24.hours,
        status = if (withStatus) StatusListToken(Uri.parse("https://example.com/status"), index) else null,
        clientStatus = StatusListToken(Uri.parse("https://example.com/client"), 0u),
        keyStorageStatus = null,
        subject = "12345678",
    )

    private fun useCase(
        credentials: List<IssuedCredential>,
        revoked: MutableList<UInt> = mutableListOf(),
        marked: MutableList<IssuedCredential> = mutableListOf(),
        mark: MarkStatusAsRevoked = MarkStatusAsRevoked { revoked.add(it.index) },
        requestedSubject: MutableList<String> = mutableListOf(),
    ) = RevokeCredentialsOfSubject(
        clock = clock,
        getActiveIssuedCredentialsBySubject = { subject, _ ->
            requestedSubject.add(subject)
            credentials
        },
        markStatusAsRevoked = mark,
        markIssuedCredentialRevoked = { credential, _ -> marked.add(credential) },
    )

    @Test
    fun `revokes every active credential of the subject and marks its record as revoked`() =
        runTest {
            val revoked = mutableListOf<UInt>()
            val marked = mutableListOf<IssuedCredential>()
            val subjects = mutableListOf<String>()
            val credentials = listOf(credential(1u), credential(2u))

            val result = useCase(credentials, revoked, marked, requestedSubject = subjects)("12345678")

            assertEquals(RevokeCredentialsOfSubject.Result(revoked = 2, failed = 0, withoutStatus = 0), result)
            assertEquals(listOf(1u, 2u), revoked)
            assertEquals(credentials, marked)
            assertEquals(listOf("12345678"), subjects)
        }

    @Test
    fun `a subject with no credentials revokes nothing`() =
        runTest {
            val revoked = mutableListOf<UInt>()
            val result = useCase(emptyList(), revoked)("12345678")
            assertEquals(RevokeCredentialsOfSubject.Result(0, 0, 0), result)
            assertEquals(emptyList(), revoked)
        }

    @Test
    fun `credentials without a status list are reported and left untouched`() =
        runTest {
            val revoked = mutableListOf<UInt>()
            val marked = mutableListOf<IssuedCredential>()

            val result = useCase(listOf(credential(1u, withStatus = false), credential(2u)), revoked, marked)("12345678")

            assertEquals(RevokeCredentialsOfSubject.Result(revoked = 1, failed = 0, withoutStatus = 1), result)
            assertEquals(listOf(2u), revoked)
            assertEquals(1, marked.size)
        }

    @Test
    fun `when the status list rejects a credential its record is kept and the others continue`() =
        runTest {
            val revoked = mutableListOf<UInt>()
            val marked = mutableListOf<IssuedCredential>()
            val mark =
                MarkStatusAsRevoked {
                    if (it.index == 1u) raise(MarkStatusAsRevoked.Error("status list unavailable")) else revoked.add(it.index)
                }

            val failing = credential(1u)
            val result = useCase(listOf(failing, credential(2u)), revoked, marked, mark)("12345678")

            assertEquals(RevokeCredentialsOfSubject.Result(revoked = 1, failed = 1, withoutStatus = 0), result)
            assertEquals(listOf(2u), revoked)
            assertEquals(false, marked.contains(failing))
        }

    @Test
    fun `an unexpected exception from the status list counts as a failure and keeps the record`() =
        runTest {
            val marked = mutableListOf<IssuedCredential>()
            val mark = MarkStatusAsRevoked { throw RuntimeException("connection reset") }

            val result = useCase(listOf(credential(1u)), marked = marked, mark = mark)("12345678")

            assertEquals(RevokeCredentialsOfSubject.Result(revoked = 0, failed = 1, withoutStatus = 0), result)
            assertEquals(emptyList(), marked)
        }
}
