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

internal class RevokePreviousWalletOnIssuanceTest {
    private val clock = Clock.System

    private fun credential(
        index: UInt,
        wallet: UInt,
        subject: String? = "12345678",
        withStatus: Boolean = true,
    ) = IssuedCredential(
        format = Format("dc+sd-jwt"),
        type = "urn:eudi:pid:1",
        issuedAt = clock.now(),
        expiresAt = clock.now() + 24.hours,
        status = if (withStatus) StatusListToken(Uri.parse("https://example.com/status"), index) else null,
        clientStatus = StatusListToken(Uri.parse("https://example.com/client"), wallet),
        keyStorageStatus = null,
        subject = subject,
    )

    private class Fixture(
        val active: List<IssuedCredential>,
        val failOn: UInt? = null,
    ) {
        val stored = mutableListOf<IssuedCredential>()
        val revoked = mutableListOf<UInt>()
        val marked = mutableListOf<IssuedCredential>()
        val reasons = mutableListOf<String>()
        val useCase =
            RevokePreviousWalletOnIssuance(
                delegate = { stored.add(it) },
                clock = Clock.System,
                getActiveIssuedCredentialsBySubject = { _, _ -> active },
                markStatusAsRevoked =
                    MarkStatusAsRevoked {
                        if (it.index == failOn) raise(MarkStatusAsRevoked.Error("status list unavailable")) else revoked.add(it.index)
                    },
                markIssuedCredentialRevoked = { c, reason ->
                    marked.add(c)
                    reasons.add(reason)
                },
            )
    }

    @Test
    fun `credentials of another wallet are revoked and the new one is stored`() =
        runTest {
            val f = Fixture(listOf(credential(1u, wallet = 10u), credential(2u, wallet = 10u)))
            val nueva = credential(3u, wallet = 20u)

            f.useCase(nueva)

            assertEquals(listOf(1u, 2u), f.revoked)
            assertEquals(2, f.marked.size)
            assertEquals(listOf(RevokePreviousWalletOnIssuance.REASON, RevokePreviousWalletOnIssuance.REASON), f.reasons)
            assertEquals(listOf(nueva), f.stored)
        }

    @Test
    fun `copies of the same wallet are never touched`() =
        runTest {
            val f = Fixture(listOf(credential(1u, wallet = 20u), credential(2u, wallet = 20u)))

            f.useCase(credential(3u, wallet = 20u))

            assertEquals(emptyList(), f.revoked)
            assertEquals(emptyList(), f.marked)
            assertEquals(1, f.stored.size)
        }

    @Test
    fun `only the other wallet is revoked when both are present`() =
        runTest {
            val f = Fixture(listOf(credential(1u, wallet = 10u), credential(2u, wallet = 20u)))

            f.useCase(credential(3u, wallet = 20u))

            assertEquals(listOf(1u), f.revoked)
        }

    @Test
    fun `a credential without subject is stored without revoking anything`() =
        runTest {
            val f = Fixture(listOf(credential(1u, wallet = 10u)))

            f.useCase(credential(3u, wallet = 20u, subject = null))

            assertEquals(emptyList(), f.revoked)
            assertEquals(1, f.stored.size)
        }

    @Test
    fun `if the status list fails the issuance still happens and that record is kept`() =
        runTest {
            val failing = credential(1u, wallet = 10u)
            val f = Fixture(listOf(failing, credential(2u, wallet = 10u)), failOn = 1u)

            f.useCase(credential(3u, wallet = 20u))

            assertEquals(listOf(2u), f.revoked)
            assertEquals(false, f.marked.contains(failing))
            assertEquals(1, f.stored.size)
        }

    @Test
    fun `an old credential without status list is only marked in the record`() =
        runTest {
            val f = Fixture(listOf(credential(1u, wallet = 10u, withStatus = false)))

            f.useCase(credential(3u, wallet = 20u))

            assertEquals(emptyList(), f.revoked)
            assertEquals(1, f.marked.size)
        }
}
