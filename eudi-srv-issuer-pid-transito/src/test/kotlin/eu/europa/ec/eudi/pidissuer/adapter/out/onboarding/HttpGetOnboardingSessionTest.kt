/*
 * Copyright (c) 2023-2026 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */
package eu.europa.ec.eudi.pidissuer.adapter.out.onboarding

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HttpGetOnboardingSessionTest {
    @Test
    fun `parses the onboarding backend session`() {
        val json =
            """
            {"id":"b0f6b7c4-1d4e-4f0d-9c0e-3b9f6a0e4c11","status":"COMPLETED","decision":"APPROVED",
             "data":{"documentNumber":"12345678","firstName":"JOSE","middleName":"GREGORIO","lastName":"MUNOZ",
                     "secondLastName":"NAVAS","birthDate":"1973-09-08","gender":"MALE"},
             "dataMatch":{"documentNumberMatch":true}}
            """.trimIndent()
        val session = HttpGetOnboardingSession.parseSession(json)
        assertEquals("APPROVED", session.decision)
        assertEquals("12345678", session.documentNumber)
        assertEquals("JOSE GREGORIO", session.firstNames)
        assertEquals("MUNOZ NAVAS", session.lastNames)
        assertEquals(LocalDate(1973, 9, 8), session.birthDate)
    }

    @Test
    fun `missing data yields empty fields`() {
        val session = HttpGetOnboardingSession.parseSession("""{"decision":null,"status":"PENDING"}""")
        assertNull(session.decision)
        assertNull(session.documentNumber)
        assertEquals("", session.firstNames)
        assertNull(session.birthDate)
    }
}
