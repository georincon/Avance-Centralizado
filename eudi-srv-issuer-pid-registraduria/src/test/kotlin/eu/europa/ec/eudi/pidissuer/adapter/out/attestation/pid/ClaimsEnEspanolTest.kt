package eu.europa.ec.eudi.pidissuer.adapter.out.attestation.pid

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClaimsEnEspanolTest {
    private val es = Locale.forLanguageTag("es")

    @Test
    fun `todos los datos del PID en SD-JWT y mdoc llevan nombre en espanol salvo los que ya lo tenian`() {
        val sinEspanol =
            (SdJwtVcPidClaims.all().conEspanol() + MsoMdocPidClaims.all().conEspanol())
                .flatMap { listOf(it) + it.nested }
                .filter { es !in it.display }
                .map { it.name }
        assertEquals(emptyList(), sinEspanol)
    }

    @Test
    fun `se anade el espanol sin quitar el ingles`() {
        val familyName = SdJwtVcPidClaims.all().conEspanol().single { it.name == "family_name" }
        assertEquals("Apellido(s)", familyName.display[es])
        assertTrue(Locale.ENGLISH in familyName.display)
    }

    @Test
    fun `no sustituye un espanol que el dato ya declara`() {
        val edad = SdJwtVcPidClaims.all().conEspanol().single { it.name == "age_equal_or_over" }
        val mayorDe18 = edad.nested.single { it.name == "18" }
        assertEquals("Mayor de 18 años", mayorDe18.display[es])
    }

    @Test
    fun `un dato desconocido queda igual`() {
        val dato = SdJwtVcPidClaims.all().first().copy(
            path = eu.europa.ec.eudi.pidissuer.domain.ClaimPath.claim("dato_que_no_existe"),
            display = emptyMap(),
            nested = emptyList(),
        )
        assertNull(dato.conEspanol().display[es])
    }
}
