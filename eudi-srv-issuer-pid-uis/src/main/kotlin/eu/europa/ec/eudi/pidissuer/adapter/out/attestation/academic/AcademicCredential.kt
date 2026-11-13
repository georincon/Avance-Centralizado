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
import kotlinx.datetime.LocalDate

/**
 * Identifier types for students
 */
enum class IdentificationType {
    CC,
    CE,
    TI,
    PAS,
    PPT,
    PEP,
    ;

    val value: String
        get() = name
}

/**
 * Academic program levels as defined by UIS
 */
enum class AcademicLevel {
    TECNICO,
    TECNOLOGICO,
    PREGRADO,
    ESPECIALIZACION,
    MAESTRIA,
    DOCTORADO,
    ;

    val value: String
        get() = name
}

/**
 * Program modality
 */
enum class ProgramModality {
    PRESENCIAL,
    DISTANCIA,
    VIRTUAL,
    HIBRIDA,
    ;

    val value: String
        get() = name
}

/**
 * Student academic status
 */
enum class AcademicStatus {
    GRADUADO,
    ACTIVO,
    RETIRADO,
    PAUSA,
    ;

    val value: String
        get() = name
}

/**
 * Represents the issuing institution
 */
data class IssuingInstitution(
    val name: InstitutionName,
    val country: CountryCode,
    val uri: HttpsUrl,
    val nit: String? = null,
) {
    typealias InstitutionName = NonBlankString
    typealias CountryCode = NonBlankString
}

/**
 * Represents an academic program
 */
data class AcademicProgram(
    val code: ProgramCode,
    val name: ProgramName,
    val level: AcademicLevel,
    val modality: ProgramModality,
    val faculty: String? = null,
    val academicUnit: String? = null,
    val awardedTitle: String,
    val durationSemesters: Int,
    val totalCredits: Int,
) {
    typealias ProgramCode = NonBlankString
    typealias ProgramName = NonBlankString
}

/**
 * Academic record of a student
 */
data class AcademicRecord(
    val program: AcademicProgram,
    val enrollmentDate: LocalDate?,
    val graduationDate: LocalDate?,
    val accumulatedAverage: String?,
    val approvedCredits: Int,
    val semestersCompleted: Int,
    val status: AcademicStatus,
)

/**
 * Student identification data
 */
data class StudentIdentification(
    val type: IdentificationType,
    val number: String,
) {
    init {
        require(number.isNotBlank()) { "Identification number cannot be blank" }
    }
}

/**
 * Complete academic credential data
 */
data class AcademicCredential(
    val institution: IssuingInstitution,
    val studentId: StudentIdentification,
    val username: Username,
    val familyName: FamilyName,
    val givenName: GivenName,
    val email: String? = null,
    val phoneNumber: String? = null,
    val academicRecord: AcademicRecord,
    val dateOfIssuance: LocalDate,
    val dateOfExpiry: LocalDate? = null,
    val documentNumber: String? = null,
) {
    typealias FamilyName = NonBlankString
    typealias GivenName = NonBlankString
    typealias Username = NonBlankString

    val program: AcademicProgram
        get() = academicRecord.program

    val academicLevel: AcademicLevel
        get() = program.level

    val isGraduated: Boolean
        get() = academicRecord.status == AcademicStatus.GRADUADO

    val creditedTitle: String
        get() = program.awardedTitle
}
