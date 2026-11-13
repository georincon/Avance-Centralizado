/*
 * Código propio de este fork (emulador de la Registraduría Nacional del Estado Civil).
 * No es parte del eudi-srv-pid-issuer original de la Comisión Europea.
 * Ver CONTEXTO-REGISTRADURIA.md para el contexto completo.
 */
package eu.europa.ec.eudi.pidissuer.port.out.registraduria

import eu.europa.ec.eudi.pidissuer.adapter.out.registraduria.Cedula
import eu.europa.ec.eudi.pidissuer.adapter.out.registraduria.CedulaDocumentNumber

/**
 * Busca un ciudadano por número de cédula. Lo usan tanto el panel admin
 * (para editar) como el adapter que arma la PidAttributes para emisión.
 */
fun interface FindCedulaByDocumentNumber {
    suspend operator fun invoke(documentNumber: CedulaDocumentNumber): Cedula?
}

/**
 * Lista ciudadanos para el panel admin, con un filtro de texto libre opcional
 * (por documento o por nombre/apellido) como en la pantalla de listado.
 */
fun interface ListCedulas {
    suspend operator fun invoke(searchQuery: String?): List<Cedula>
}

/**
 * Crea o actualiza un ciudadano (upsert por documentNumber). Devuelve el
 * registro guardado, con su [Cedula.id] asignado si era nuevo.
 */
fun interface SaveCedula {
    suspend operator fun invoke(cedula: Cedula): Cedula
}

/**
 * Borra un ciudadano del panel admin. Es una función exclusiva del administrador y NO tiene relación con la
 * revocación: borrar al ciudadano no revoca sus credenciales (hay que revocarlas antes, si corresponde) y revocar
 * nunca borra al ciudadano. Tampoco borra al usuario ya sincronizado en Keycloak, si lo hubiera.
 */
fun interface DeleteCedula {
    suspend operator fun invoke(documentNumber: CedulaDocumentNumber): Boolean
}
