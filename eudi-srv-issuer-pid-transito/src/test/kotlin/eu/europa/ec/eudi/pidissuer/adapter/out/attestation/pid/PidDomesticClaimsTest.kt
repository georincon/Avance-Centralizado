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
package eu.europa.ec.eudi.pidissuer.adapter.out.attestation.pid

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PidDomesticClaimsTest {
    @Test
    fun `domestic namespace follows ARF PID_06 (pid namespace + country code + version)`() {
        assertEquals("eu.europa.ec.eudi.pid.co.1", MsoMdocPidCoClaims.nameSpace)
        assertEquals("eu.europa.ec.eudi.pid.1", MsoMdocPidClaims.nameSpace)
    }

    @Test
    fun `mdoc and sd-jwt PID expose the licencia specific claims`() {
        val names = listOf("estatura_cm", "grupo_sanguineo_rh", "fecha_expedicion", "lugar_expedicion")
        val mdocAll = MsoMdocPidClaims.all().map { it.name }
        val sdAll = SdJwtVcPidClaims.all().map { it.name }
        names.forEach {
            assertTrue(it in mdocAll, "mdoc missing $it")
            assertTrue(it in sdAll, "sd-jwt missing $it")
        }
    }

    @Test
    fun `age claims are a local extension of the PID (age_over_NN in mdoc, age_equal_or_over in sd-jwt)`() {
        assertEquals(listOf(18, 21), EdadesVerificadas)
        val mdoc = MsoMdocPidClaims.all().map { it.name }
        EdadesVerificadas.forEach { assertTrue("age_over_$it" in mdoc, "mdoc missing age_over_$it") }
        val sd = SdJwtVcPidClaims.all().single { it.name == "age_equal_or_over" }
        assertEquals(EdadesVerificadas.size, sd.nested.size)
    }
}
