/*
 * Código propio de este fork (emulador de la Dirección de Tránsito de Bucaramanga).
 * No es parte del eudi-srv-pid-issuer original de la Comisión Europea.
 */
package eu.europa.ec.eudi.pidissuer.adapter.out.transito

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CategoriaLicenciaTest {
    private val expedicion = LocalDate(2024, 3, 10)

    @Test
    fun `the eight national categories exist`() {
        assertEquals(listOf("A1", "A2", "B1", "B2", "B3", "C1", "C2", "C3"), CategoriaLicencia.entries.map { it.codigo })
    }

    @Test
    fun `particular service lasts 10 years under 60, 5 years from 60 to 80 and 1 year above 80`() {
        assertEquals(LocalDate(2034, 3, 10), CategoriaLicencia.B1.vigenciaHasta(LocalDate(1990, 1, 1), expedicion))
        assertEquals(LocalDate(2029, 3, 10), CategoriaLicencia.B1.vigenciaHasta(LocalDate(1960, 1, 1), expedicion))
        assertEquals(LocalDate(2025, 3, 10), CategoriaLicencia.B1.vigenciaHasta(LocalDate(1940, 1, 1), expedicion))
    }

    @Test
    fun `public service always lasts 3 years`() {
        listOf(CategoriaLicencia.C1, CategoriaLicencia.C2, CategoriaLicencia.C3).forEach {
            assertEquals(LocalDate(2027, 3, 10), it.vigenciaHasta(LocalDate(1990, 1, 1), expedicion))
        }
    }

    @Test
    fun `minimum age is enforced at the category issue date`() {
        val nacimiento = LocalDate(2008, 6, 1) // 15 años y 9 meses al expedir
        val base = licenciaDePrueba()
        fun con(cat: CategoriaLicencia) =
            base.copy(
                fechaNacimiento = nacimiento,
                categorias = listOf(LicenciaCategoria(cat, LocalDate(2024, 3, 10), LocalDate(2034, 3, 10))),
            )
        assertFailsWith<IllegalArgumentException> { con(CategoriaLicencia.A2) }
        assertFailsWith<IllegalArgumentException> { con(CategoriaLicencia.C1) }
        // A los 16 sí
        base.copy(
            fechaNacimiento = LocalDate(2007, 6, 1),
            categorias = listOf(LicenciaCategoria(CategoriaLicencia.A2, LocalDate(2024, 3, 10), LocalDate(2034, 3, 10))),
        )
    }

    @Test
    fun `a category cannot be repeated and must expire after its issue date`() {
        val cat = LicenciaCategoria(CategoriaLicencia.B1, LocalDate(2020, 1, 1), LocalDate(2030, 1, 1))
        assertFailsWith<IllegalArgumentException> { licenciaDePrueba(categorias = listOf(cat, cat)) }
        assertFailsWith<IllegalArgumentException> { LicenciaCategoria(CategoriaLicencia.B1, LocalDate(2020, 1, 1), LocalDate(2020, 1, 1)) }
    }

    @Test
    fun `only active licences with a photo and a valid category can be issued`() {
        val hoy = java.time.LocalDate.of(2026, 10, 30)
        assert(licenciaDePrueba().esEmisible(hoy))
        assert(!licenciaDePrueba(estado = EstadoLicencia.SUSPENDIDA).esEmisible(hoy))
        assert(!licenciaDePrueba(estado = EstadoLicencia.CANCELADA).esEmisible(hoy))
        assert(!licenciaDePrueba(foto = null).esEmisible(hoy))
        val vencida = listOf(LicenciaCategoria(CategoriaLicencia.B1, LocalDate(2010, 1, 1), LocalDate(2020, 1, 1)))
        assert(!licenciaDePrueba(categorias = vencida).esEmisible(hoy))
    }
}
