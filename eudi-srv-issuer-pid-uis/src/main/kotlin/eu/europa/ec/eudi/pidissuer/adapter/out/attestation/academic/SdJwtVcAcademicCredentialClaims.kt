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

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import eu.europa.ec.eudi.pidissuer.domain.ClaimDefinition
import eu.europa.ec.eudi.pidissuer.domain.ClaimPath
import java.util.*

/**
 * Claims definitions for Academic Credential SD-JWT VC
 * Based on EBSI Diploma Use Case and Colombian academic standards
 */
object SdJwtVcAcademicCredentialClaims {
    val IssuingAuthority: ClaimDefinition =
        ClaimDefinition(
            path = ClaimPath.claim("issuing_authority"),
            mandatory = true,
            display = mapOf(Locale.ENGLISH to "Issuing Authority"),
        )

    val IssuingCountry: ClaimDefinition =
        ClaimDefinition(
            path = ClaimPath.claim("issuing_country"),
            mandatory = true,
            display = mapOf(Locale.ENGLISH to "Issuing Country"),
        )

    val DateOfIssuance: ClaimDefinition =
        ClaimDefinition(
            path = ClaimPath.claim("date_of_issuance"),
            mandatory = true,
            display = mapOf(Locale.ENGLISH to "Date of Issuance"),
        )

    val DateOfExpiry: ClaimDefinition =
        ClaimDefinition(
            path = ClaimPath.claim("date_of_expiry"),
            mandatory = false,
            display = mapOf(Locale.ENGLISH to "Date of Expiry"),
        )

    val FamilyName: ClaimDefinition =
        ClaimDefinition(
            path = ClaimPath.claim("family_name"),
            mandatory = true,
            display = mapOf(Locale.ENGLISH to "Family Name(s)"),
        )

    val GivenName: ClaimDefinition =
        ClaimDefinition(
            path = ClaimPath.claim("given_name"),
            mandatory = true,
            display = mapOf(Locale.ENGLISH to "Given Name(s)"),
        )

    val StudentId: ClaimDefinition =
        ClaimDefinition(
            path = ClaimPath.claim("student_id"),
            mandatory = true,
            display = mapOf(Locale.ENGLISH to "Student Identification Number"),
        )

    val IdentificationType: ClaimDefinition =
        ClaimDefinition(
            path = ClaimPath.claim("identification_type"),
            mandatory = true,
            display = mapOf(Locale.ENGLISH to "Identification Type"),
        )

    val Email: ClaimDefinition =
        ClaimDefinition(
            path = ClaimPath.claim("email"),
            mandatory = false,
            display = mapOf(Locale.ENGLISH to "Email Address"),
        )

    val PhoneNumber: ClaimDefinition =
        ClaimDefinition(
            path = ClaimPath.claim("phone_number"),
            mandatory = false,
            display = mapOf(Locale.ENGLISH to "Phone Number"),
        )

    val ProgramCode: ClaimDefinition =
        ClaimDefinition(
            path = ClaimPath.claim("program_code"),
            mandatory = true,
            display = mapOf(Locale.ENGLISH to "Academic Program Code"),
        )

    val ProgramName: ClaimDefinition =
        ClaimDefinition(
            path = ClaimPath.claim("program_name"),
            mandatory = true,
            display = mapOf(Locale.ENGLISH to "Academic Program Name"),
        )

    val AcademicLevel: ClaimDefinition =
        ClaimDefinition(
            path = ClaimPath.claim("academic_level"),
            mandatory = true,
            display = mapOf(Locale.ENGLISH to "Academic Level"),
        )

    val AwardedTitle: ClaimDefinition =
        ClaimDefinition(
            path = ClaimPath.claim("awarded_title"),
            mandatory = true,
            display = mapOf(Locale.ENGLISH to "Awarded Title"),
        )

    val Faculty: ClaimDefinition =
        ClaimDefinition(
            path = ClaimPath.claim("faculty"),
            mandatory = false,
            display = mapOf(Locale.ENGLISH to "Faculty"),
        )

    val AcademicUnit: ClaimDefinition =
        ClaimDefinition(
            path = ClaimPath.claim("academic_unit"),
            mandatory = false,
            display = mapOf(Locale.ENGLISH to "Academic Unit"),
        )

    val ProgramModality: ClaimDefinition =
        ClaimDefinition(
            path = ClaimPath.claim("program_modality"),
            mandatory = true,
            display = mapOf(Locale.ENGLISH to "Program Modality"),
        )

    val DurationSemesters: ClaimDefinition =
        ClaimDefinition(
            path = ClaimPath.claim("duration_semesters"),
            mandatory = true,
            display = mapOf(Locale.ENGLISH to "Program Duration (Semesters)"),
        )

    val TotalCredits: ClaimDefinition =
        ClaimDefinition(
            path = ClaimPath.claim("total_credits"),
            mandatory = true,
            display = mapOf(Locale.ENGLISH to "Total Credits"),
        )

    val EnrollmentDate: ClaimDefinition =
        ClaimDefinition(
            path = ClaimPath.claim("enrollment_date"),
            mandatory = false,
            display = mapOf(Locale.ENGLISH to "Enrollment Date"),
        )

    val GraduationDate: ClaimDefinition =
        ClaimDefinition(
            path = ClaimPath.claim("graduation_date"),
            mandatory = false,
            display = mapOf(Locale.ENGLISH to "Graduation Date"),
        )

    val ApprovedCredits: ClaimDefinition =
        ClaimDefinition(
            path = ClaimPath.claim("approved_credits"),
            mandatory = true,
            display = mapOf(Locale.ENGLISH to "Approved Credits"),
        )

    val SemestersCompleted: ClaimDefinition =
        ClaimDefinition(
            path = ClaimPath.claim("semesters_completed"),
            mandatory = true,
            display = mapOf(Locale.ENGLISH to "Semesters Completed"),
        )

    val AcademicStatus: ClaimDefinition =
        ClaimDefinition(
            path = ClaimPath.claim("academic_status"),
            mandatory = true,
            display = mapOf(Locale.ENGLISH to "Academic Status"),
        )

    val AccumulatedAverage: ClaimDefinition =
        ClaimDefinition(
            path = ClaimPath.claim("accumulated_average"),
            mandatory = false,
            display = mapOf(Locale.ENGLISH to "Accumulated Average"),
        )

    val DocumentNumber: ClaimDefinition =
        ClaimDefinition(
            path = ClaimPath.claim("document_number"),
            mandatory = false,
            display = mapOf(Locale.ENGLISH to "Document Number"),
        )

    val IssuingInstitutionName: ClaimDefinition =
        ClaimDefinition(
            path = ClaimPath.claim("issuing_institution_name"),
            mandatory = true,
            display = mapOf(Locale.ENGLISH to "Issuing Institution Name"),
        )

    fun all(): NonEmptyList<ClaimDefinition> =
        nonEmptyListOf(
            IssuingAuthority,
            IssuingCountry,
            DateOfIssuance,
            DateOfExpiry,
            FamilyName,
            GivenName,
            StudentId,
            IdentificationType,
            Email,
            PhoneNumber,
            ProgramCode,
            ProgramName,
            AcademicLevel,
            AwardedTitle,
            Faculty,
            AcademicUnit,
            ProgramModality,
            DurationSemesters,
            TotalCredits,
            EnrollmentDate,
            GraduationDate,
            ApprovedCredits,
            SemestersCompleted,
            AcademicStatus,
            AccumulatedAverage,
            DocumentNumber,
            IssuingInstitutionName,
        )
}
