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

package eu.europa.ec.corelogic.extension

import eu.europa.ec.eudi.wallet.document.format.MsoMdocFormat
import eu.europa.ec.eudi.wallet.document.format.SdJwtVcFormat
import eu.europa.ec.eudi.wallet.transactionLogging.presentation.PresentedClaim
import eu.europa.ec.eudi.wallet.transactionLogging.presentation.PresentedDocument
import org.junit.Assert.assertEquals
import org.junit.Test

class TestPresentedDocumentExtensions {

    //region sharedClaims

    @Test
    fun `Given an SD-JWT VC with only the age claim requested, When sharedClaims is called, Then the technical claims are dropped`() {
        // Given
        val ageClaim = claim("age_equal_or_over", "18", value = true)
        val document = sdJwtDocument(
            claim("cnf", "jwk", "crv", value = "P-256"),
            claim("cnf", "jwk", "kty", value = "EC"),
            claim("cnf", "jwk", "x", value = "KJurd"),
            claim("cnf", "jwk", "y", value = "OE0Ye"),
            claim("exp", value = 1795352267),
            claim("iat", value = 1792673867),
            claim("iss", value = "https://issuer.example"),
            ageClaim,
            claim("nbf", value = 1792673887),
            claim("vct", value = "urn:eudi:pid:1"),
        )

        // When
        val result = document.sharedClaims()

        // Then
        assertEquals(listOf(ageClaim), result)
    }

    @Test
    fun `Given an SD-JWT VC with personal data, When sharedClaims is called, Then every personal claim is kept`() {
        // Given
        val givenName = claim("given_name", value = "JAN")
        val street = claim("address", "street_address", value = "Calle 1")
        val document = sdJwtDocument(claim("iss", value = "https://issuer.example"), givenName, street)

        // When
        val result = document.sharedClaims()

        // Then
        assertEquals(listOf(givenName, street), result)
    }

    @Test
    fun `Given a nested claim named like a technical one, When sharedClaims is called, Then it is kept because only top-level claims are technical`() {
        // Given
        val nested = claim("address", "iss", value = "something")
        val document = sdJwtDocument(nested)

        // When
        val result = document.sharedClaims()

        // Then
        assertEquals(listOf(nested), result)
    }

    @Test
    fun `Given an SD-JWT VC with only technical claims, When sharedClaims is called, Then the result is empty`() {
        // Given
        val document = sdJwtDocument(claim("iss", value = "x"), claim("vct", value = "y"))

        // When
        val result = document.sharedClaims()

        // Then
        assertEquals(emptyList<PresentedClaim>(), result)
    }

    @Test
    fun `Given an mdoc, When sharedClaims is called, Then all its claims are returned untouched`() {
        // Given
        val claims = listOf(
            claim("eu.europa.ec.eudi.pid.1", "family_name", value = "ANDERSSON"),
            claim("eu.europa.ec.eudi.pid.1", "age_over_18", value = true),
        )
        val document = PresentedDocument(
            format = MsoMdocFormat("eu.europa.ec.eudi.pid.1"),
            metadata = null,
            claims = claims,
        )

        // When
        val result = document.sharedClaims()

        // Then
        assertEquals(claims, result)
    }

    //endregion

    private fun claim(vararg path: String, value: Any): PresentedClaim =
        PresentedClaim(path = path.toList(), value = value, rawValue = value, metadata = null)

    private fun sdJwtDocument(vararg claims: PresentedClaim) = PresentedDocument(
        format = SdJwtVcFormat("urn:eudi:pid:1"),
        metadata = null,
        claims = claims.toList(),
    )
}
