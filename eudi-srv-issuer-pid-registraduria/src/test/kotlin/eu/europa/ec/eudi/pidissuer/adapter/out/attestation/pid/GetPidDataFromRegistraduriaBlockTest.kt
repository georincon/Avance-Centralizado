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

import arrow.core.nonEmptySetOf
import arrow.core.raise.either
import com.eygraber.uri.Uri
import com.nimbusds.oauth2.sdk.token.DPoPAccessToken
import eu.europa.ec.eudi.pidissuer.adapter.out.registraduria.Cedula
import eu.europa.ec.eudi.pidissuer.adapter.out.registraduria.CedulaDocumentNumber
import eu.europa.ec.eudi.pidissuer.adapter.out.registraduria.FotoReferencia
import eu.europa.ec.eudi.pidissuer.adapter.out.registraduria.Sexo
import eu.europa.ec.eudi.pidissuer.domain.ClientStatus
import eu.europa.ec.eudi.pidissuer.domain.Scope
import eu.europa.ec.eudi.pidissuer.domain.StatusClaim
import eu.europa.ec.eudi.pidissuer.domain.StatusListToken
import eu.europa.ec.eudi.pidissuer.port.input.AuthorizationContext
import eu.europa.ec.eudi.pidissuer.port.input.IssueCredentialError
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/** Un titular con la emisión bloqueada no recibe credenciales aunque sus datos sigan en la base. */
internal class GetPidDataFromRegistraduriaBlockTest {
    private val cedula =
        Cedula(
            documentNumber = CedulaDocumentNumber("12345678"),
            apellidos = "MUNOZ NAVAS",
            nombres = "JOSE GREGORIO",
            fechaNacimiento = LocalDate(1973, 9, 8),
            lugarNacimientoCiudad = "BUCARAMANGA",
            sexo = Sexo.M,
            fotoReferencia = FotoReferencia(byteArrayOf(1, 2, 3), "image/jpeg"),
        )

    private val authorizationContext =
        AuthorizationContext(
            "12345678",
            DPoPAccessToken("token"),
            nonEmptySetOf(Scope("test")),
            clientStatus =
                ClientStatus(
                    status = StatusClaim(StatusListToken(Uri.parse("https://example.com/wia/42"), 1337u)),
                    expiresAt = Clock.System.now() + 32.days,
                ),
        )

    private fun source(blocked: Boolean) =
        GetPidDataFromRegistraduria(
            issuerCountry = IsoCountry("CO"),
            issuingJurisdiction = null,
            findCedulaByDocumentNumber = { cedula },
            isSubjectBlocked = { blocked },
        )

    @Test
    fun `a blocked holder gets no data to issue`() =
        runTest {
            val source = source(blocked = true)
            val result = context(authorizationContext) { either { source() } }
            assertTrue(result.isLeft())
            assertEquals(IssueCredentialError.AttestationDatasetNotFound, result.leftOrNull())
        }

    @Test
    fun `a holder who is not blocked is issued as usual`() =
        runTest {
            val source = source(blocked = false)
            val result = context(authorizationContext) { either { source() } }
            assertTrue(result.isRight(), "an unblocked holder must still get a PID: ${result.leftOrNull()}")
        }
}
