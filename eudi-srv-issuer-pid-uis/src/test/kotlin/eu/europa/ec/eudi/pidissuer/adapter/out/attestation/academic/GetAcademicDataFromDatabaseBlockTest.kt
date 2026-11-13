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

import arrow.core.nonEmptySetOf
import arrow.core.raise.either
import com.eygraber.uri.Uri
import com.nimbusds.oauth2.sdk.token.DPoPAccessToken
import eu.europa.ec.eudi.pidissuer.domain.ClientStatus
import eu.europa.ec.eudi.pidissuer.domain.Scope
import eu.europa.ec.eudi.pidissuer.domain.StatusClaim
import eu.europa.ec.eudi.pidissuer.domain.StatusListToken
import eu.europa.ec.eudi.pidissuer.port.input.AuthorizationContext
import eu.europa.ec.eudi.pidissuer.port.input.IssueCredentialError
import kotlinx.coroutines.test.runTest
import org.springframework.r2dbc.core.DatabaseClient
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/** Un estudiante con la emisión bloqueada no recibe credenciales, y ni siquiera se consulta su registro académico. */
internal class GetAcademicDataFromDatabaseBlockTest {
    /** Una base de datos que falla si alguien la usa. */
    private val unusableDatabase: DatabaseClient =
        Proxy.newProxyInstance(DatabaseClient::class.java.classLoader, arrayOf(DatabaseClient::class.java)) { _, method, _ ->
            error("no se debía consultar la base académica (${method.name})")
        } as DatabaseClient

    private val authorizationContext =
        AuthorizationContext(
            "jperez",
            DPoPAccessToken("token"),
            nonEmptySetOf(Scope("test")),
            clientStatus =
                ClientStatus(
                    status = StatusClaim(StatusListToken(Uri.parse("https://example.com/wia/42"), 1337u)),
                    expiresAt = Clock.System.now() + 32.days,
                ),
        )

    @Test
    fun `a blocked student gets no data and the academic database is not even queried`() =
        runTest {
            val source =
                GetAcademicDataFromDatabase(
                    unusableDatabase,
                    AcademicDatabaseConfig("UIS", "CO", "https://www.uis.edu.co"),
                    isSubjectBlocked = { it == "jperez" },
                )

            val result = context(authorizationContext) { either { source() } }

            assertTrue(result.isLeft())
            assertEquals(IssueCredentialError.AttestationDatasetNotFound, result.leftOrNull())
        }
}
