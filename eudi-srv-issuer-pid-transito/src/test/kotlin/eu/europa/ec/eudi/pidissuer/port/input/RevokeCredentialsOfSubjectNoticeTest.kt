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
import eu.europa.ec.eudi.pidissuer.port.out.notification.DeliveryResult
import eu.europa.ec.eudi.pidissuer.port.out.persistence.RevocationNotice
import eu.europa.ec.eudi.pidissuer.port.out.status.MarkStatusAsRevoked
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

/** Al revocar, el titular debe recibir un aviso (CIR 2024/2977 art. 5(3)). */
class RevokeCredentialsOfSubjectNoticeTest {
    private val clock = Clock.System

    private fun credential(
        index: UInt,
        type: String = "urn:eudi:pid:1",
    ) = IssuedCredential(
        format = Format("dc+sd-jwt"),
        type = type,
        issuedAt = clock.now(),
        expiresAt = clock.now() + 24.hours,
        status = StatusListToken(Uri.parse("https://example.com/status"), index),
        clientStatus = StatusListToken(Uri.parse("https://example.com/client"), 0u),
        keyStorageStatus = null,
        subject = "12345678",
    )

    private fun run(
        credentials: List<IssuedCredential>,
        mark: MarkStatusAsRevoked = MarkStatusAsRevoked { },
        stored: MutableList<RevocationNotice> = mutableListOf(),
        reason: String = "fraud",
    ): List<RevocationNotice> {
        val notices =
            RevocationNoticeService(
                clock = clock,
                store = {
                    stored.add(it)
                    stored.size.toLong()
                },
                update = { _, _, _, _ -> true },
                deliver = { _, _ -> DeliveryResult.NoChannel("sin canal") },
                getContact = { null },
                isSubjectBlocked = { false },
                issuerName = "la Registraduría",
            )
        val useCase =
            RevokeCredentialsOfSubject(
                clock = clock,
                getActiveIssuedCredentialsBySubject = { _, _ -> credentials },
                markStatusAsRevoked = mark,
                markIssuedCredentialRevoked = { _, _ -> },
                noticeService = notices,
            )
        kotlinx.coroutines.test.runTest { useCase("12345678", reason) }
        return stored
    }

    @Test
    fun `revoking credentials registers one notice with the credential types and the reason`() {
        val stored = run(listOf(credential(1u), credential(2u, "urn:co:uis:carnet-estudiantil:1")))

        val notice = stored.single()
        assertEquals("12345678", notice.subject)
        assertEquals(listOf("urn:eudi:pid:1", "urn:co:uis:carnet-estudiantil:1"), notice.credentialTypes)
        assertEquals("fraud", notice.reason)
    }

    @Test
    fun `nothing revoked means nothing to notify`() {
        assertEquals(emptyList(), run(emptyList()))
    }

    @Test
    fun `if the status list rejects every credential nobody is notified of a revocation that did not happen`() {
        val stored = run(listOf(credential(1u)), mark = MarkStatusAsRevoked { raise(MarkStatusAsRevoked.Error("caído")) })
        assertEquals(emptyList(), stored)
    }

    @Test
    fun `only the credentials that were really revoked are named in the notice`() {
        val stored =
            run(
                listOf(credential(1u), credential(2u, "urn:co:uis:carnet-estudiantil:1")),
                mark = MarkStatusAsRevoked { if (it.index == 2u) raise(MarkStatusAsRevoked.Error("caído")) },
            )
        assertEquals(listOf("urn:eudi:pid:1"), stored.single().credentialTypes)
    }
}
