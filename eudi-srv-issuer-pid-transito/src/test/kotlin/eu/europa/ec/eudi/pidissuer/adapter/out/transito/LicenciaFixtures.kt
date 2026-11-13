/*
 * Código propio de este fork (emulador de la Dirección de Tránsito de Bucaramanga).
 * No es parte del eudi-srv-pid-issuer original de la Comisión Europea.
 */
package eu.europa.ec.eudi.pidissuer.adapter.out.transito

import kotlinx.datetime.LocalDate

/** Licencia de prueba: titular de 1973, categoría B1 vigente hasta 2099 y foto. */
fun licenciaDePrueba(
    doc: String = "12345678",
    nombres: String = "JOSE GREGORIO",
    apellidos: String = "MUNOZ NAVAS",
    estado: EstadoLicencia = EstadoLicencia.ACTIVA,
    categorias: List<LicenciaCategoria> =
        listOf(LicenciaCategoria(CategoriaLicencia.B1, LocalDate(2020, 1, 15), LocalDate(2099, 1, 15))),
    foto: FotoReferencia? = FotoReferencia(byteArrayOf(1, 2, 3), "image/jpeg"),
) = Licencia(
    documentNumber = LicenciaDocumentNumber(doc),
    apellidos = apellidos,
    nombres = nombres,
    fechaNacimiento = LocalDate(1973, 9, 8),
    lugarNacimientoCiudad = "BUCARAMANGA",
    lugarNacimientoDepartamento = "SANTANDER",
    sexo = Sexo.M,
    estaturaCm = 175,
    grupoSanguineo = GrupoSanguineo.O_POS,
    fechaExpedicion = LocalDate(2020, 1, 15),
    estado = estado,
    fotoReferencia = foto,
    categorias = categorias,
)
