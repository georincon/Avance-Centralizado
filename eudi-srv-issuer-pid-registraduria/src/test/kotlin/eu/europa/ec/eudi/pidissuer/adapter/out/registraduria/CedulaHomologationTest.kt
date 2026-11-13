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
package eu.europa.ec.eudi.pidissuer.adapter.out.registraduria

import eu.europa.ec.eudi.pidissuer.port.out.registraduria.OnboardingSession
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CedulaHomologationTest {
    private val cedula =
        Cedula(
            documentNumber = CedulaDocumentNumber("12345678"),
            apellidos = "MUÑOZ NÁVAS",
            nombres = "JOSÉ GREGORIO",
            fechaNacimiento = LocalDate(1973, 9, 8),
            lugarNacimientoCiudad = "BUCARAMANGA",
            sexo = Sexo.M,
        )

    private fun session(
        first: String = "JOSE GREGORIO",
        last: String = "MUNOZ NAVAS",
        birth: LocalDate? = LocalDate(1973, 9, 8),
    ) = OnboardingSession("APPROVED", "COMPLETED", "12345678", first, last, birth)

    @Test
    fun `accent and case differences are ignored`() {
        val result = CedulaHomologation.check(session(first = "jose gregorio"), cedula)
        assertTrue(result.ok, "reasons=${result.reasons}")
    }

    @Test
    fun `small OCR typos are accepted when similarity is at least 0_90`() {
        // 1 letra de 14 -> 0.93
        val result = CedulaHomologation.check(session(last = "MUNOZ NAVAZ"), cedula)
        assertTrue(result.ok, "reasons=${result.reasons}")
    }

    @Test
    fun `different birth date is rejected`() {
        val result = CedulaHomologation.check(session(birth = LocalDate(1973, 9, 9)), cedula)
        assertEquals(listOf("BIRTH_DATE_MISMATCH"), result.reasons)
    }

    @Test
    fun `different names are rejected`() {
        val result = CedulaHomologation.check(session(first = "PEDRO PABLO", last = "GOMEZ RUIZ"), cedula)
        assertEquals(listOf("GIVEN_NAMES_MISMATCH", "FAMILY_NAMES_MISMATCH"), result.reasons)
    }

    @Test
    fun `missing birth date is rejected`() {
        assertEquals(listOf("BIRTH_DATE_MISMATCH"), CedulaHomologation.check(session(birth = null), cedula).reasons)
    }
}
