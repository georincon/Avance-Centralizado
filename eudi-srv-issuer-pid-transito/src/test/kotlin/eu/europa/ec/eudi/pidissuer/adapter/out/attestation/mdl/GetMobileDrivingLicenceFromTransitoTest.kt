/*
 * Código propio de este fork (emulador de la Dirección de Tránsito de Bucaramanga).
 * No es parte del eudi-srv-pid-issuer original de la Comisión Europea.
 */
package eu.europa.ec.eudi.pidissuer.adapter.out.attestation.mdl

import arrow.core.nonEmptySetOf
import arrow.core.raise.either
import com.eygraber.uri.Uri
import com.nimbusds.oauth2.sdk.token.DPoPAccessToken
import eu.europa.ec.eudi.pidissuer.adapter.out.transito.CategoriaLicencia
import eu.europa.ec.eudi.pidissuer.adapter.out.transito.EstadoLicencia
import eu.europa.ec.eudi.pidissuer.adapter.out.transito.Licencia
import eu.europa.ec.eudi.pidissuer.adapter.out.transito.LicenciaCategoria
import eu.europa.ec.eudi.pidissuer.adapter.out.transito.licenciaDePrueba
import eu.europa.ec.eudi.pidissuer.domain.ClientStatus
import eu.europa.ec.eudi.pidissuer.domain.Scope
import eu.europa.ec.eudi.pidissuer.domain.StatusClaim
import eu.europa.ec.eudi.pidissuer.domain.StatusListToken
import eu.europa.ec.eudi.pidissuer.port.input.AuthorizationContext
import eu.europa.ec.eudi.pidissuer.port.input.IssueCredentialError
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import java.time.LocalDate as JLocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

internal class GetMobileDrivingLicenceFromTransitoTest {
    private val hoy = JLocalDate.of(2026, 10, 30)

    private fun source(
        licencia: Licencia?,
        blocked: Boolean = false,
    ) = GetMobileDrivingLicenceFromTransito(
        issuingJurisdiction = Latin150("CO-SAN"),
        findLicenciaByDocumentNumber = { licencia },
        isSubjectBlocked = { blocked },
        hoy = { hoy },
    )

    private fun context() =
        AuthorizationContext(
            "12345678",
            DPoPAccessToken("token"),
            nonEmptySetOf(Scope("test")),
            clientStatus =
                ClientStatus(
                    status = StatusClaim(StatusListToken(Uri.parse("https://revocation_url/wia-statuslists/42"), 1337u)),
                    expiresAt = Clock.System.now() + 32.days,
                ),
        )

    private suspend fun GetMobileDrivingLicenceFromTransito.run() = context(context()) { either { this@run() } }

    @Test
    fun `an active licence becomes an mDL with national categories and Colombian issuer`() =
        runTest {
            val licencia =
                licenciaDePrueba(
                    categorias =
                        listOf(
                            LicenciaCategoria(CategoriaLicencia.A1, LocalDate(2020, 1, 15), LocalDate(2030, 1, 15)),
                            LicenciaCategoria(CategoriaLicencia.C1, LocalDate(2024, 5, 1), LocalDate(2027, 5, 1)),
                        ),
                ).copy(usaLentes = true)

            val mdl = assertNotNull(source(licencia).run().getOrNull())

            assertEquals("CO", mdl.issuer.country.countryCode.code)
            assertEquals("CO", mdl.issuer.country.distinguishingSign.code)
            assertEquals("Dirección de Tránsito de Bucaramanga", mdl.issuer.authority.value)
            assertEquals("CO-SAN", mdl.issuer.jurisdiction?.value)
            assertEquals("12345678", mdl.documentNumber.value)
            assertEquals(setOf("A1", "C1"), mdl.privileges.map { it.vehicleCategory.code }.toSet())
            assertEquals(JLocalDate.of(2030, 1, 15), mdl.issueAndExpiry.expiresAt)
            // A1: máximo 125 c.c.; con lentes: restricción 01 en todas las categorías
            val a1 = mdl.privileges.first { it.vehicleCategory == VehicleCategory.CO_A1 }
            assertEquals(setOf("S03", "01"), a1.restrictions?.map { it.code }?.toSet())
            val c1 = mdl.privileges.first { it.vehicleCategory == VehicleCategory.CO_C1 }
            assertEquals(setOf("01"), c1.restrictions?.map { it.code }?.toSet())
        }

    @Test
    fun `expired categories are left out of the privileges`() =
        runTest {
            val licencia =
                licenciaDePrueba(
                    categorias =
                        listOf(
                            LicenciaCategoria(CategoriaLicencia.B1, LocalDate(2010, 1, 15), LocalDate(2020, 1, 15)),
                            LicenciaCategoria(CategoriaLicencia.A2, LocalDate(2022, 1, 15), LocalDate(2032, 1, 15)),
                        ),
                )
            val mdl = assertNotNull(source(licencia).run().getOrNull())
            assertEquals(setOf("A2"), mdl.privileges.map { it.vehicleCategory.code }.toSet())
        }

    @Test
    fun `nothing is issued when the licence is missing, blocked, not active, without photo or fully expired`() =
        runTest {
            val vencida = listOf(LicenciaCategoria(CategoriaLicencia.B1, LocalDate(2010, 1, 15), LocalDate(2020, 1, 15)))
            val casos =
                listOf(
                    source(null),
                    source(licenciaDePrueba(), blocked = true),
                    source(licenciaDePrueba(estado = EstadoLicencia.SUSPENDIDA)),
                    source(licenciaDePrueba(estado = EstadoLicencia.CANCELADA)),
                    source(licenciaDePrueba(foto = null)),
                    source(licenciaDePrueba(categorias = vencida)),
                )
            casos.forEach { assertIs<IssueCredentialError.AttestationDatasetNotFound>(it.run().leftOrNull()) }
        }
}
