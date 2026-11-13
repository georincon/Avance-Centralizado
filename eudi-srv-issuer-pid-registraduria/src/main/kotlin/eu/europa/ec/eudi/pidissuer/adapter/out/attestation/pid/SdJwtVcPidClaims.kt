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
package eu.europa.ec.eudi.pidissuer.adapter.out.attestation.pid

import eu.europa.ec.eudi.pidissuer.adapter.out.attestation.*
import eu.europa.ec.eudi.pidissuer.domain.ClaimDefinition
import eu.europa.ec.eudi.pidissuer.domain.ClaimPath
import java.util.*

internal object SdJwtVcPidClaims {
    val FamilyName = OidcFamilyName
    val GivenName = OidcGivenName
    val BirthDate = OidcBirthDate
    val BirthFamilyName = OidcAssuranceBirthFamilyName
    val BirthGivenName = OidcAssuranceBirthGivenName
    val PlaceOfBirth = OidcAssurancePlaceOfBirth
    val Address = OidcAddressClaim
    val Sex =
        ClaimDefinition(
            path = ClaimPath.claim("sex"),
            mandatory = false,
            display = mapOf(Locale.ENGLISH to "Sex"),
        )
    val Nationalities = OidcAssuranceNationalities
    val IssuingAuthority =
        ClaimDefinition(
            path = ClaimPath.claim("issuing_authority"),
            mandatory = true,
            display = mapOf(Locale.ENGLISH to "Issuing Authority"),
        )
    val DocumentNumber =
        ClaimDefinition(
            path = ClaimPath.claim("document_number"),
            mandatory = false,
            display = mapOf(Locale.ENGLISH to "Document Number"),
        )
    val PersonalAdministrativeNumber =
        ClaimDefinition(
            path = ClaimPath.claim("personal_administrative_number"),
            mandatory = false,
            display = mapOf(Locale.ENGLISH to "Personal Administrative Number"),
        )
    val IssuingCountry =
        ClaimDefinition(
            path = ClaimPath.claim("issuing_country"),
            mandatory = true,
            display = mapOf(Locale.ENGLISH to "Issuing Country"),
        )
    val IssuingJurisdiction =
        ClaimDefinition(
            path = ClaimPath.claim("issuing_jurisdiction"),
            mandatory = false,
            display = mapOf(Locale.ENGLISH to "Issuing Jurisdiction"),
        )
    val Email =
        ClaimDefinition(
            path = ClaimPath.claim("email"),
            mandatory = false,
            display = mapOf(Locale.ENGLISH to "Email Address"),
        )
    val PhoneNumber =
        ClaimDefinition(
            path = ClaimPath.claim("phone_number"),
            mandatory = false,
            display = mapOf(Locale.ENGLISH to "Mobile Phone Number"),
        )
    val Picture =
        ClaimDefinition(
            path = ClaimPath.claim("picture"),
            mandatory = false,
            display = mapOf(Locale.ENGLISH to "Portrait Image"),
        )
    val DateOfExpiry =
        ClaimDefinition(
            path = ClaimPath.claim("date_of_expiry"),
            mandatory = false,
            display = mapOf(Locale.ENGLISH to "Expiry Date"),
        )
    val DateOfIssuance =
        ClaimDefinition(
            path = ClaimPath.claim("date_of_issuance"),
            mandatory = false,
            display = mapOf(Locale.ENGLISH to "Issuance Date"),
        )
    val AttestationLegalCategory =
        ClaimDefinition(
            path = ClaimPath.claim("attestation_legal_category"),
            mandatory = false,
            display =
                mapOf(
                    Locale.ENGLISH to "Attestation Legal Category",
                ),
        )

    // Atributos nacionales de la cédula colombiana (ARF 3.0 PID_14: tipo nacional que extiende urn:eudi:pid:1)
    val HeightCm =
        ClaimDefinition(
            path = ClaimPath.claim("estatura_cm"),
            mandatory = false,
            display = mapOf(Locale.ENGLISH to "Height (cm)"),
        )
    val BloodType =
        ClaimDefinition(
            path = ClaimPath.claim("grupo_sanguineo_rh"),
            mandatory = false,
            display = mapOf(Locale.ENGLISH to "Blood Type and Rh"),
        )
    val DocumentIssueDate =
        ClaimDefinition(
            path = ClaimPath.claim("fecha_expedicion"),
            mandatory = false,
            display = mapOf(Locale.ENGLISH to "Document Issue Date"),
        )
    val DocumentIssuePlace =
        ClaimDefinition(
            path = ClaimPath.claim("lugar_expedicion"),
            mandatory = false,
            display = mapOf(Locale.ENGLISH to "Document Issue Place"),
        )


    /**
     * Parche (pid-issuer/imagen, v0.11.0-edad.patch): `age_equal_or_over`, un
     * objeto con un booleano por umbral de [EdadesVerificadas] ("18", "21").
     * Cada umbral es divulgable por separado: un verificador puede pedir solo
     * `["age_equal_or_over", "18"]`.
     */
    object AgeEqualOrOver {
        private val es = Locale.forLanguageTag("es")
        private val path = ClaimPath.claim("age_equal_or_over")

        fun umbral(edad: Int): ClaimDefinition =
            ClaimDefinition(
                path = path.claim(edad.toString()),
                mandatory = false,
                display = mapOf(es to "Mayor de $edad años", Locale.ENGLISH to "Age $edad or over"),
            )

        val attribute: ClaimDefinition =
            ClaimDefinition(
                path = path,
                mandatory = false,
                display = mapOf(es to "Verificación de edad", Locale.ENGLISH to "Age Equal or Over"),
                nested = EdadesVerificadas.map(::umbral),
            )
    }

    fun all(): List<ClaimDefinition> =
        listOf(
            FamilyName,
            GivenName,
            BirthDate,
            AgeEqualOrOver.attribute,
            PlaceOfBirth.attribute,
            Nationalities,
            Address.attribute,
            PersonalAdministrativeNumber,
            Picture,
            BirthFamilyName,
            BirthGivenName,
            Sex,
            Email,
            PhoneNumber,
            DateOfExpiry,
            IssuingAuthority,
            IssuingCountry,
            DocumentNumber,
            IssuingJurisdiction,
            DateOfIssuance,
            AttestationLegalCategory,
            HeightCm,
            BloodType,
            DocumentIssueDate,
            DocumentIssuePlace,
        )
}
