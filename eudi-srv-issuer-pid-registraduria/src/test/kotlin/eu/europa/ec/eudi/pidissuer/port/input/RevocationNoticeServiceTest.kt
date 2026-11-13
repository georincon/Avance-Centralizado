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

import eu.europa.ec.eudi.pidissuer.port.out.notification.DeliveryResult
import eu.europa.ec.eudi.pidissuer.port.out.notification.SubjectContact
import eu.europa.ec.eudi.pidissuer.port.out.persistence.NoticeStatus
import eu.europa.ec.eudi.pidissuer.port.out.persistence.RevocationNotice
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class RevocationNoticeServiceTest {
    private val now = Instant.parse("2026-10-30T15:00:00Z")
    private val clock =
        object : Clock {
            override fun now(): Instant = now
        }

    private data class Update(
        val id: Long,
        val status: NoticeStatus,
        val channel: String?,
        val detail: String?,
    )

    private class Recorder {
        val stored = mutableListOf<RevocationNotice>()
        val updates = mutableListOf<Update>()
    }

    private fun service(
        rec: Recorder,
        result: DeliveryResult = DeliveryResult.Sent("webhook"),
        contact: SubjectContact? = SubjectContact("a@b.co", null),
        blocked: Boolean = false,
        failStore: Boolean = false,
    ) = RevocationNoticeService(
        clock = clock,
        store = {
            if (failStore) error("base de datos caída")
            rec.stored.add(it)
            rec.stored.size.toLong()
        },
        update = { id, status, channel, detail ->
            rec.updates.add(Update(id, status, channel, detail))
            true
        },
        deliver = { _, _ -> result },
        getContact = { contact },
        isSubjectBlocked = { blocked },
        issuerName = "la Registraduría",
    )

    @Test
    fun `registers the notice with a 24 hour deadline and the reason in plain language`() =
        runTest {
            val rec = Recorder()
            service(rec).notifyRevocation("12345678", listOf("urn:eudi:pid:1", "urn:eudi:pid:1"), "fraud")

            val notice = rec.stored.single()
            assertEquals("12345678", notice.subject)
            assertEquals(listOf("urn:eudi:pid:1"), notice.credentialTypes)
            assertEquals(now, notice.revokedAt)
            assertEquals(now + 24.hours, notice.deadlineAt)
            assertTrue(notice.message.contains("uso indebido"))
        }

    @Test
    fun `a delivered notice is marked as sent through its channel`() =
        runTest {
            val rec = Recorder()
            service(rec, DeliveryResult.Sent("webhook")).notifyRevocation("1", listOf("x"), "other")
            assertEquals(Update(1, NoticeStatus.SENT, "webhook", null), rec.updates.single())
        }

    @Test
    fun `without a channel or contact the notice stays pending with the reason, for a manual notice`() =
        runTest {
            val rec = Recorder()
            service(rec, DeliveryResult.NoChannel("el titular no tiene datos de contacto registrados")).notifyRevocation("1", listOf("x"), "other")
            val update = rec.updates.single()
            assertEquals(NoticeStatus.PENDING, update.status)
            assertEquals("el titular no tiene datos de contacto registrados", update.detail)
        }

    @Test
    fun `a failed delivery stays open as failed`() =
        runTest {
            val rec = Recorder()
            service(rec, DeliveryResult.Failed("503")).notifyRevocation("1", listOf("x"), "other")
            assertEquals(Update(1, NoticeStatus.FAILED, null, "503"), rec.updates.single())
            assertTrue(rec.updates.single().status.isOpen)
        }

    @Test
    fun `the message tells a blocked holder that a new credential is not possible`() =
        runTest {
            val rec = Recorder()
            service(rec, blocked = true).notifyRevocation("1", listOf("x"), "fraud")
            assertTrue(rec.stored.single().message.contains("no es posible emitirle una nueva credencial"))
        }

    @Test
    fun `a failure while registering the notice never breaks the revocation`() =
        runTest {
            val rec = Recorder()
            service(rec, failStore = true).notifyRevocation("1", listOf("x"), "other") // no debe lanzar
            assertEquals(emptyList(), rec.updates)
        }
}
