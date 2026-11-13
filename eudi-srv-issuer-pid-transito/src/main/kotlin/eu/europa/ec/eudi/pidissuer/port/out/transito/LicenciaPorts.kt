/*
 * Código propio de este fork (emulador de la Dirección de Tránsito de Bucaramanga).
 * No es parte del eudi-srv-pid-issuer original de la Comisión Europea.
 * Ver README.md para el contexto completo.
 */
package eu.europa.ec.eudi.pidissuer.port.out.transito

import eu.europa.ec.eudi.pidissuer.adapter.out.transito.Licencia
import eu.europa.ec.eudi.pidissuer.adapter.out.transito.LicenciaDocumentNumber

/**
 * Busca un ciudadano por número de cédula. Lo usan tanto el panel admin
 * (para editar) como el adapter que arma la PidAttributes para emisión.
 */
fun interface FindLicenciaByDocumentNumber {
    suspend operator fun invoke(documentNumber: LicenciaDocumentNumber): Licencia?
}

/**
 * Lista ciudadanos para el panel admin, con un filtro de texto libre opcional
 * (por documento o por nombre/apellido) como en la pantalla de listado.
 */
fun interface ListLicencias {
    suspend operator fun invoke(searchQuery: String?): List<Licencia>
}

/**
 * Crea o actualiza un ciudadano (upsert por documentNumber). Devuelve el
 * registro guardado, con su [Licencia.id] asignado si era nuevo.
 */
fun interface SaveLicencia {
    suspend operator fun invoke(licencia: Licencia): Licencia
}

/**
 * Borra un ciudadano del panel admin. Es una función exclusiva del administrador y NO tiene relación con la
 * revocación: borrar al ciudadano no revoca sus credenciales (hay que revocarlas antes, si corresponde) y revocar
 * nunca borra al ciudadano. Tampoco borra al usuario ya sincronizado en Keycloak, si lo hubiera.
 */
fun interface DeleteLicencia {
    suspend operator fun invoke(documentNumber: LicenciaDocumentNumber): Boolean
}
