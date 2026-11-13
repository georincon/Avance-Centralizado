/*
 * Copyright (c) 2026 European Commission
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

package eu.europa.ec.testfeature.util

import androidx.annotation.VisibleForTesting
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import org.mockito.kotlin.whenever

@VisibleForTesting(otherwise = VisibleForTesting.Companion.NONE)
object StringResourceProviderMocker {

    /**
     * Mocks ResourceProvider.getString(...) for each (resId → returnValue) pair.
     */
    fun mockResourceProviderStrings(
        resourceProvider: ResourceProvider,
        pairs: List<Pair<Int, String>>,
    ) {
        pairs.forEach { (resId, returnValue) ->
            whenever(resourceProvider.getString(resId)).thenReturn(returnValue)
        }
    }

    fun mockGetDocumentDetailsStrings(
        resourceProvider: ResourceProvider,
        availableCredentials: Int,
        totalCredentials: Int,
    ) {
        mockCreateDocumentCredentialsInfoStrings(
            resourceProvider = resourceProvider,
            availableCredentials = availableCredentials,
            totalCredentials = totalCredentials
        )

        mockTransformToDocumentDetailsDomainStrings(resourceProvider)
    }

    fun mockCreateDocumentCredentialsInfoStrings(
        resourceProvider: ResourceProvider,
        availableCredentials: Int,
        totalCredentials: Int,
    ) {
        whenever(
            resourceProvider.getString(
                R.string.document_details_document_credentials_info_text,
                availableCredentials,
                totalCredentials
            )
        ).thenReturn("$availableCredentials/$totalCredentials instances remaining")
    }

    fun mockTransformToDocumentDetailsDomainStrings(resourceProvider: ResourceProvider) {
        mockCreateKeyValueStrings(resourceProvider)
    }

    fun mockCreateKeyValueStrings(resourceProvider: ResourceProvider) {
        val mockedStrings = listOf(
            R.string.document_details_boolean_item_true_readable_value to "yes",
            R.string.document_details_boolean_item_false_readable_value to "no",
        )

        mockResourceProviderStrings(resourceProvider, mockedStrings)
        mockGetGenderValueStrings(resourceProvider)
        mockDocumentClaimLabelStrings(resourceProvider)
    }

    /**
     * Mocks every local claim-label string used by [eu.europa.ec.commonfeature.util.getReadableNameFromIdentifier]'s
     * local Spanish label table, so tests exercising the claim-name resolution pipeline with
     * well-known PID/mDL identifiers (family_name, given_name, address, ...) don't hit an
     * unstubbed getString(...) call.
     */
    fun mockDocumentClaimLabelStrings(resourceProvider: ResourceProvider) {
        val mockedStrings = listOf(
            R.string.document_claim_family_name to "Apellido(s)",
            R.string.document_claim_given_name to "Nombre(s)",
            R.string.document_claim_birth_family_name to "Apellido(s) de nacimiento",
            R.string.document_claim_birth_given_name to "Nombre(s) de nacimiento",
            R.string.document_claim_birth_date to "Fecha de nacimiento",
            R.string.document_claim_birthdate to "Fecha de nacimiento",
            R.string.document_claim_age_over_18 to "Mayor de 18 años",
            R.string.document_claim_age_in_years to "Edad en años",
            R.string.document_claim_age_birth_year to "Año de nacimiento",
            R.string.document_claim_age_over_65 to "Mayor de 65 años",
            R.string.document_claim_birth_city to "Ciudad de nacimiento",
            R.string.document_claim_nationality to "Nacionalidad",
            R.string.document_claim_nationalities to "Nacionalidad(es)",
            R.string.document_claim_gender to "Sexo",
            R.string.document_claim_sex to "Sexo",
            R.string.document_claim_portrait to "Retrato",
            R.string.document_claim_picture to "Foto",
            R.string.document_claim_signature_usual_mark to "Firma habitual",
            R.string.document_claim_place_of_birth to "Lugar de nacimiento",
            R.string.document_claim_locality to "Localidad",
            R.string.document_claim_region to "Región",
            R.string.document_claim_country to "País",
            R.string.document_claim_address to "Dirección",
            R.string.document_claim_street_address to "Calle y número",
            R.string.document_claim_formatted to "Dirección completa",
            R.string.document_claim_postal_code to "Código postal",
            R.string.document_claim_resident_address to "Dirección de residencia",
            R.string.document_claim_resident_country to "País de residencia",
            R.string.document_claim_resident_state to "Provincia/Estado de residencia",
            R.string.document_claim_resident_city to "Ciudad de residencia",
            R.string.document_claim_resident_postal_code to "Código postal de residencia",
            R.string.document_claim_resident_street to "Calle de residencia",
            R.string.document_claim_resident_house_number to "Número de vivienda",
            R.string.document_claim_document_number to "Número de documento",
            R.string.document_claim_administrative_number to "Número administrativo",
            R.string.document_claim_personal_administrative_number to "Número administrativo personal",
            R.string.document_claim_issuing_country to "País emisor",
            R.string.document_claim_issuing_authority to "Autoridad emisora",
            R.string.document_claim_issuing_jurisdiction to "Jurisdicción emisora",
            R.string.document_claim_issuance_date to "Fecha de emisión",
            R.string.document_claim_expiry_date to "Fecha de caducidad",
            R.string.document_claim_email_address to "Correo electrónico",
            R.string.document_claim_email to "Correo electrónico",
            R.string.document_claim_phone_number to "Número de teléfono",
            R.string.document_claim_driving_privileges to "Categorías de conducción",
            R.string.document_claim_un_distinguishing_sign to "Signo distintivo de la ONU",
            R.string.document_claim_user_pseudonym to "Seudónimo",
            R.string.document_claim_attestation_legal_category to "Categoría legal de la certificación",
        )

        mockResourceProviderStrings(resourceProvider, mockedStrings)
    }

    fun mockGetGenderValueStrings(resourceProvider: ResourceProvider) {
        val mockedStrings = listOf(
            R.string.request_gender_male to "Male",
            R.string.request_gender_female to "Female",
            R.string.request_gender_not_known to "Not known",
            R.string.request_gender_not_applicable to "Not applicable",
        )

        mockResourceProviderStrings(resourceProvider, mockedStrings)
    }

    fun mockTransformToUiItemsStrings(
        resourceProvider: ResourceProvider,
    ) {
        mockCreateKeyValueStrings(resourceProvider)

        whenever(resourceProvider.getString(R.string.request_collapsed_supporting_text))
            .thenReturn(mockedRequestCollapsedSupportingText)

        whenever(resourceProvider.getLocale())
            .thenReturn(mockedDefaultLocale)
    }

    fun mockIssuerName(
        resourceProvider: ResourceProvider,
        name: String
    ) {
        whenever(resourceProvider.getString(R.string.issuance_success_header_issuer_default_name))
            .thenReturn(name)
    }

    fun mockGetUiItemsStrings(
        resourceProvider: ResourceProvider,
        supportingText: String,
    ) {
        whenever(resourceProvider.getString(R.string.document_success_collapsed_supporting_text))
            .thenReturn(supportingText)
    }
}