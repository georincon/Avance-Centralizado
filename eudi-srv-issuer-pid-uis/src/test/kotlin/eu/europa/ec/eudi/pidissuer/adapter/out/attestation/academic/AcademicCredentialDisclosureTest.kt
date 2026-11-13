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

package eu.europa.ec.eudi.pidissuer.adapter.out.attestation.academic

import eu.europa.ec.eudi.pidissuer.domain.HttpsUrl
import eu.europa.ec.eudi.pidissuer.domain.NonBlankString
import eu.europa.ec.eudi.sdjwt.dsl.Disclosable
import eu.europa.ec.eudi.sdjwt.dsl.values.sdJwt
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Minimizacion de datos de la credencial academica: solo viajan siempre los datos de quien emite y las
 * fechas de la credencial; todo lo demas lo entrega la wallet solo si el verificador lo pide.
 */
class AcademicCredentialDisclosureTest {

    private val programa =
        AcademicProgram(
            code = NonBlankString("PRE-001"),
            name = NonBlankString("Ingenieria de Sistemas"),
            level = AcademicLevel.PREGRADO,
            modality = ProgramModality.PRESENCIAL,
            faculty = "Ingenierias",
            academicUnit = "Escuela de Sistemas",
            awardedTitle = "Ingeniero de Sistemas",
            durationSemesters = 10,
            totalCredits = 170,
        )

    private val credencial =
        AcademicCredential(
            institution =
            IssuingInstitution(
                name = NonBlankString("Universidad Industrial de Santander"),
                country = NonBlankString("CO"),
                uri = HttpsUrl.unsafe("https://civica-desarrollo.avance.org.co/uis-issuer"),
            ),
            studentId = StudentIdentification(IdentificationType.CC, "91296304"),
            username = NonBlankString("grincon"),
            familyName = NonBlankString("Rincon Navas"),
            givenName = NonBlankString("Geovani Gregorio"),
            email = "estudiante@example.com",
            phoneNumber = "3000000000",
            academicRecord =
            AcademicRecord(
                program = programa,
                enrollmentDate = LocalDate(2016, 2, 1),
                graduationDate = LocalDate(2023, 9, 12),
                accumulatedAverage = "4.2",
                approvedCredits = 170,
                semestersCompleted = 10,
                status = AcademicStatus.GRADUADO,
            ),
            dateOfIssuance = LocalDate(2026, 10, 21),
            dateOfExpiry = LocalDate(2027, 10, 21),
            documentNumber = "91296304",
        )

    private val objeto = sdJwt { academicCredential(credencial) }

    @Test
    fun `solo la institucion y las fechas de la credencial viajan siempre`() {
        val siempre = objeto.content.filterValues { it is Disclosable.NeverSelectively<*> }.keys
        assertEquals(
            setOf("issuing_authority", "issuing_country", "issuing_institution_name", "date_of_issuance", "date_of_expiry"),
            siempre,
        )
    }

    @Test
    fun `los datos personales y academicos se pueden ocultar uno a uno`() {
        val ocultables = objeto.content.filterValues { it is Disclosable.AlwaysSelectively<*> }.keys
        val esperados =
            setOf(
                "family_name", "given_name", "student_id", "identification_type", "document_number",
                "email", "phone_number", "program_code", "program_name", "academic_level", "awarded_title",
                "faculty", "academic_unit", "program_modality", "duration_semesters", "total_credits",
                "enrollment_date", "graduation_date", "approved_credits", "semesters_completed",
                "academic_status", "accumulated_average",
            )
        assertEquals(esperados, ocultables)
    }

    @Test
    fun `un verificador que pide solo el nivel no recibe la cedula ni el usuario estudiantil`() {
        val visibles = objeto.content.filterValues { it is Disclosable.NeverSelectively<*> }.keys
        assertTrue("document_number" !in visibles)
        assertTrue("student_id" !in visibles)
        assertTrue("academic_level" !in visibles)
    }
}
