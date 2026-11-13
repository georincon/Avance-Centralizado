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

import com.eygraber.uri.Uri
import eu.europa.ec.eudi.pidissuer.domain.Format
import eu.europa.ec.eudi.pidissuer.domain.IssuedCredential
import eu.europa.ec.eudi.pidissuer.domain.StatusListToken
import eu.europa.ec.eudi.pidissuer.port.out.status.RevokeWalletInstances
import eu.europa.ec.eudi.pidissuer.port.out.status.WalletRevocation
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

internal class RevokeWalletOfSubjectTest {
    private val clock = Clock.System

    private fun token(
        name: String,
        index: UInt,
    ) = StatusListToken(Uri.parse("https://example.com/$name"), index)

    private fun credential(
        wallet: UInt,
        keys: UInt?,
    ) = IssuedCredential(
        format = Format("dc+sd-jwt"),
        type = "urn:eudi:pid:1",
        issuedAt = clock.now(),
        expiresAt = clock.now() + 24.hours,
        status = token("status", 1u),
        clientStatus = token("client", wallet),
        keyStorageStatus = keys?.let { token("keys", it) },
        subject = "12345678",
    )

    @Test
    fun `the wallet and key storage entries of all credentials are collected without repeats`() =
        runTest {
            val useCase =
                RevokeWalletOfSubject(
                    clock,
                    { _, _ -> listOf(credential(10u, 5u), credential(10u, 5u), credential(10u, null), credential(11u, 6u)) },
                    null,
                )

            assertEquals(
                listOf(token("client", 10u), token("keys", 5u), token("client", 11u), token("keys", 6u)),
                useCase.entriesOf("12345678"),
            )
        }

    @Test
    fun `entries are sent to the wallet provider and its answer is returned`() =
        runTest {
            val sent = mutableListOf<List<StatusListToken>>()
            val useCase =
                RevokeWalletOfSubject(
                    clock,
                    { _, _ -> emptyList() },
                    RevokeWalletInstances { sent.add(it); WalletRevocation(revoked = it.size, failed = 0) },
                )

            val entries = listOf(token("client", 10u), token("keys", 5u))
            assertEquals(WalletRevocation(2, 0), useCase(entries))
            assertEquals(listOf(entries), sent)
        }

    @Test
    fun `without a wallet provider configured nothing is attempted and it is reported`() =
        runTest {
            val useCase = RevokeWalletOfSubject(clock, { _, _ -> emptyList() }, null)
            assertEquals(WalletRevocation(0, 0, configured = false), useCase(listOf(token("client", 10u))))
        }

    @Test
    fun `no entries means nothing to revoke`() =
        runTest {
            var called = false
            val useCase = RevokeWalletOfSubject(clock, { _, _ -> emptyList() }, RevokeWalletInstances { called = true; WalletRevocation(0, 0) })
            assertEquals(WalletRevocation(0, 0), useCase(emptyList()))
            assertEquals(false, called)
        }
}
