package eu.europa.ec.eudi.pidissuer.adapter.out.attestation.pid

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EdadPidTest {
    private val hoy = LocalDate(2026, 10, 21)

    @Test
    fun `cumple 18 el mismo dia`() = assertTrue(cumpleEdad(LocalDate(2008, 10, 21), 18, hoy))

    @Test
    fun `no cumple 18 el dia anterior`() = assertFalse(cumpleEdad(LocalDate(2008, 10, 22), 18, hoy))

    @Test
    fun `adulto de 25 anos cumple 18 y 21`() {
        assertTrue(cumpleEdad(LocalDate(2001, 3, 5), 18, hoy))
        assertTrue(cumpleEdad(LocalDate(2001, 3, 5), 21, hoy))
    }

    @Test
    fun `19 anos cumple 18 pero no 21`() {
        assertTrue(cumpleEdad(LocalDate(2007, 1, 1), 18, hoy))
        assertFalse(cumpleEdad(LocalDate(2007, 1, 1), 21, hoy))
    }

    @Test
    fun `nacido un 29 de febrero cumple el 28 en anos no bisiestos`() {
        assertTrue(cumpleEdad(LocalDate(2008, 2, 29), 18, LocalDate(2026, 2, 28)))
        assertFalse(cumpleEdad(LocalDate(2008, 2, 29), 18, LocalDate(2026, 2, 27)))
    }
}
