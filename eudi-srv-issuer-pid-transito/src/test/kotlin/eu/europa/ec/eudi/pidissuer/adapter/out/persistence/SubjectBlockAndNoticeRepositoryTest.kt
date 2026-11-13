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

import eu.europa.ec.eudi.pidissuer.PidIssuerApplicationTest
import eu.europa.ec.eudi.pidissuer.port.out.persistence.BlockSubject
import eu.europa.ec.eudi.pidissuer.port.out.persistence.IsSubjectBlocked
import eu.europa.ec.eudi.pidissuer.port.out.persistence.ListBlockedSubjects
import eu.europa.ec.eudi.pidissuer.port.out.persistence.ListRevocationNotices
import eu.europa.ec.eudi.pidissuer.port.out.persistence.NoticeStatus
import eu.europa.ec.eudi.pidissuer.port.out.persistence.RevocationNotice
import eu.europa.ec.eudi.pidissuer.port.out.persistence.StoreRevocationNotice
import eu.europa.ec.eudi.pidissuer.port.out.persistence.UnblockSubject
import eu.europa.ec.eudi.pidissuer.port.out.persistence.UpdateRevocationNotice
import kotlinx.coroutines.test.runTest
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

@PidIssuerApplicationTest
class SubjectBlockAndNoticeRepositoryTest {
    @Autowired private lateinit var isBlocked: IsSubjectBlocked

    @Autowired private lateinit var block: BlockSubject

    @Autowired private lateinit var unblock: UnblockSubject

    @Autowired private lateinit var listBlocked: ListBlockedSubjects

    @Autowired private lateinit var store: StoreRevocationNotice

    @Autowired private lateinit var listNotices: ListRevocationNotices

    @Autowired private lateinit var update: UpdateRevocationNotice

    private fun subject() = "titular-${UUID.randomUUID()}"

    @Test
    fun `a subject is blocked until the block is released, and can be blocked again`() =
        runTest {
            val s = subject()
            assertFalse(isBlocked(s))

            assertTrue(block(s, "fraud"), "first block")
            assertTrue(isBlocked(s))
            assertTrue(s in listBlocked())

            assertTrue(unblock(s))
            assertFalse(isBlocked(s))
            assertFalse(s in listBlocked())

            assertTrue(block(s, "legal"), "a released subject can be blocked again")
            assertTrue(isBlocked(s))
        }

    @Test
    fun `blocking twice keeps a single active block and unblocking something not blocked changes nothing`() =
        runTest {
            val s = subject()
            assertTrue(block(s, "fraud"))
            assertFalse(block(s, "fraud"), "already blocked")
            assertTrue(unblock(s))
            assertFalse(unblock(s), "nothing left to release")
        }

    @Test
    fun `blocks are independent per subject`() =
        runTest {
            val a = subject()
            val b = subject()
            block(a, "fraud")
            assertTrue(isBlocked(a))
            assertFalse(isBlocked(b))
        }

    private fun notice(
        subject: String,
        deadlineInHours: Long = 24,
    ): RevocationNotice {
        val now = Clock.System.now()
        return RevocationNotice(
            subject = subject,
            credentialTypes = listOf("urn:eudi:pid:1", "urn:co:uis:carnet-estudiantil:1"),
            reason = "fraud",
            message = "Su credencial fue revocada",
            revokedAt = now,
            deadlineAt = now + deadlineInHours.hours,
        )
    }

    @Test
    fun `a stored notice is open until it is delivered, and keeps its data`() =
        runTest {
            val s = subject()
            val id = store(notice(s))

            val open = listNotices(true).single { it.id == id }
            assertEquals(s, open.subject)
            assertEquals(listOf("urn:eudi:pid:1", "urn:co:uis:carnet-estudiantil:1"), open.credentialTypes)
            assertEquals(NoticeStatus.PENDING, open.status)
            assertNull(open.notifiedAt)

            assertTrue(update(id, NoticeStatus.MANUAL_DONE, "manual", "avisado por teléfono"))

            assertTrue(listNotices(true).none { it.id == id }, "a delivered notice is no longer open")
            val done = listNotices(false).single { it.id == id }
            assertEquals(NoticeStatus.MANUAL_DONE, done.status)
            assertEquals("manual", done.channel)
            assertNotNull(done.notifiedAt)
        }

    @Test
    fun `a pending notice can record why it could not be delivered and a failed one stays open`() =
        runTest {
            val id = store(notice(subject()))
            update(id, NoticeStatus.PENDING, null, "el titular no tiene datos de contacto registrados")
            update(id, NoticeStatus.FAILED, null, "503")

            val failed = listNotices(true).single { it.id == id }
            assertEquals(NoticeStatus.FAILED, failed.status)
            assertEquals("503", failed.detail)
            assertNull(failed.notifiedAt)
        }

    @Test
    fun `open notices come with the most urgent deadline first`() =
        runTest {
            val late = store(notice(subject(), deadlineInHours = 48))
            val soon = store(notice(subject(), deadlineInHours = 2))

            val order = listNotices(true).map { it.id }
            assertTrue(order.indexOf(soon) < order.indexOf(late))
        }

    @Test
    fun `updating a notice that does not exist changes nothing`() =
        runTest {
            assertFalse(update(-1, NoticeStatus.MANUAL_DONE, "manual", null))
        }
}
