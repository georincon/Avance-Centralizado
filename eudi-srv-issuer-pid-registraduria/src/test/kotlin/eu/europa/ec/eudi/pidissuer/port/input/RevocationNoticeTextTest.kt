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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class RevocationNoticeTextTest {
    // 2026-10-30 15:30 UTC = 10:30 en Colombia (UTC-5)
    private val at = Instant.parse("2026-10-30T15:30:00Z")

    @Test
    fun `the notice says who revoked what, when and why, in plain language`() {
        val text = RevocationNoticeText.build("la Registraduría", listOf("urn:eudi:pid:1"), "fraud", at, blocked = false)

        assertTrue(text.contains("Identificación personal (PID)"))
        assertTrue(text.contains("la Registraduría"))
        assertTrue(text.contains("30/10/2026 10:30"), "the date must be shown in Colombian time: $text")
        assertTrue(text.contains("se detectó un posible uso indebido o suplantación de identidad"))
        assertTrue(text.contains("no se puede reactivar"))
    }

    @Test
    fun `it tells the holder whether they can ask for a new credential`() {
        val free = RevocationNoticeText.build("la UIS", listOf("urn:eudi:academic:credential:1"), "user-request", at, blocked = false)
        val blocked = RevocationNoticeText.build("la UIS", listOf("urn:eudi:academic:credential:1"), "user-request", at, blocked = true)

        assertTrue(free.contains("Puede solicitar una nueva credencial"))
        assertFalse(free.contains("no es posible emitirle"))
        assertTrue(blocked.contains("Por ahora no es posible emitirle una nueva credencial"))
        assertFalse(blocked.contains("Puede solicitar una nueva credencial"))
    }

    @Test
    fun `an unknown reason is reported as a decision of the issuer, never as the raw code`() {
        val text = RevocationNoticeText.build("la UIS", listOf("x"), "algo-raro", at, blocked = false)
        assertTrue(text.contains("una decisión de la entidad emisora"))
        assertFalse(text.contains("algo-raro"))
    }

    @Test
    fun `repeated credential types are named once and unknown ones are kept as they are`() {
        val text = RevocationNoticeText.build("la UIS", listOf("urn:co:uis:carnet-estudiantil:1", "urn:co:uis:carnet-estudiantil:1", "tipo-nuevo"), "other", at, false)
        assertEquals(1, Regex("Carnet estudiantil").findAll(text).count())
        assertTrue(text.contains("tipo-nuevo"))
    }

    @Test
    fun `every reason offered by the panel has its own wording`() {
        RevocationNoticeText.REASON_CODES.filter { it != "other" }.forEach {
            assertFalse(
                RevocationNoticeText.reasonLabel(it) == RevocationNoticeText.reasonLabel("other"),
                "reason '$it' has no specific wording",
            )
        }
    }
}
